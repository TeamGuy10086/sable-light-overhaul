import gzip
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

spec = importlib.util.spec_from_file_location('migration', Path(__file__).with_name('migrate-namespace.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
OLD, NEW = 'oldlight', 'sablelightoverhaul'


def string(value):
    value = value.encode() if isinstance(value, str) else value
    return struct.pack('>H', len(value)) + value


def compound(*tags):
    return b'\x0a\0\0' + b''.join(tags) + b'\0'


def text_tag(key, value):
    return b'\x08' + string(key) + string(value)


def region(nbt, external=False, codec=2):
    packed = m.encode(nbt, codec)
    chunk = struct.pack('>I', 1 if external else len(packed) + 1) + bytes([codec | (128 if external else 0)])
    if not external:
        chunk += packed
    count = (len(chunk) + 4095) // 4096
    header = ((2 << 8) | count).to_bytes(4, 'big') + bytes(4092) + b'\0\0\0\x7b' + bytes(4092)
    return header + chunk + bytes(count * 4096 - len(chunk)), packed


class MigrationTest(unittest.TestCase):
    def test_ids_keys_and_modified_utf8(self):
        src = compound(text_tag(OLD + '_flashlight', OLD + ':spotlight'), text_tag('note', b'\xc0\x80'))
        dst, count = m.rewrite_nbt(src, OLD, NEW)
        self.assertEqual(count, 2)
        self.assertIn((NEW + ':spotlight').encode(), dst)
        self.assertIn(b'\xc0\x80', dst)
        self.assertEqual(m.rewrite_nbt(dst, OLD, NEW), (dst, 0))

    def test_foreign_namespace_and_numbers_preserved(self):
        src = compound(text_tag('id', 'createbigcannons:autocannon'),
                       text_tag('other', 'someoldlight:lamp'), b'\x0b' + string('array') + struct.pack('>iii', 2, 17, -42))
        self.assertEqual(m.rewrite_nbt(src, OLD, NEW), (src, 0))

    def test_nested_palette_list(self):
        item = text_tag('Name', OLD + ':spotlight') + b'\0'
        src = compound(b'\x09' + string('palette') + b'\x0a' + struct.pack('>i', 1) + item)
        dst, count = m.rewrite_nbt(src, OLD, NEW)
        self.assertEqual(count, 1)
        self.assertIn((NEW + ':spotlight').encode(), dst)

    def test_truncated_nbt_rejected(self):
        with self.assertRaises(ValueError):
            m.rewrite_nbt(compound(text_tag('id', OLD))[:-1], OLD, NEW)

    def test_key_collision_rejected(self):
        with self.assertRaisesRegex(ValueError, 'collision'):
            m.rewrite_nbt(compound(text_tag(OLD + '_x', 'a'), text_tag(NEW + '_x', 'b')), OLD, NEW)

    def test_region_preserves_timestamps_and_payload(self):
        with tempfile.TemporaryDirectory() as td:
            src, dst = Path(td) / 'r.0.0.mca', Path(td) / 'out.mca'
            raw, _ = region(compound(text_tag('Name', OLD + ':spotlight')))
            src.write_bytes(raw)
            self.assertEqual(m.rewrite_region(src, dst, OLD, NEW), 1)
            output = dst.read_bytes()
            self.assertEqual(output[4096:8192], raw[4096:8192])
            length = int.from_bytes(output[8192:8196], 'big')
            self.assertIn(NEW.encode(), zlib.decompress(output[8197:8196 + length]))
            self.assertEqual(src.read_bytes(), raw)

    def test_external_chunk(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            target = root / 'output'
            target.mkdir()
            src = root / 'r.-1.2.mca'
            raw, packed = region(compound(text_tag('id', OLD + ':lamp')), True)
            src.write_bytes(raw)
            (root / 'c.-32.64.mcc').write_bytes(packed)
            self.assertEqual(m.rewrite_region(src, target / src.name, OLD, NEW), 1)
            self.assertIn(NEW.encode(), zlib.decompress((target / 'c.-32.64.mcc').read_bytes()))

    def test_preallocated_empty_region_is_copied_through(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            src, dst = root / 'r.3.7.mca', root / 'out.mca'
            src.write_bytes(b'')
            self.assertEqual(m.rewrite_region(src, dst, OLD, NEW), 0)
            self.assertFalse(dst.exists())

    def test_reports_binary_stores_it_cannot_rewrite(self):
        with tempfile.TemporaryDirectory() as td:
            src, out = Path(td) / 'source', Path(td) / 'output'
            (src / 'voxy' / 'storage').mkdir(parents=True)
            (src / 'voxy' / 'storage' / '00001.sst').write_bytes(b'\x28\xb5\x2f\xfd\x00')
            (src / 'vss-lod').mkdir()
            (src / 'vss-lod' / 'lod.bin').write_bytes(b'old')
            (src / 'sublevels').mkdir()
            (src / 'sublevels' / 'r.0.0.slvlr').write_bytes(bytes(4096))
            report = m.migrate(src, out, OLD, NEW)
            self.assertEqual(report['status'], 'COMPLETE')
            self.assertEqual([s['path'] for s in report['unhandled_binary_stores']],
                             ['voxy', 'vss-lod', 'sublevels'])
            self.assertEqual([s['files'] for s in report['unhandled_binary_stores']], [1, 1, 1])
            self.assertTrue(report['warnings'])
            self.assertTrue((out / 'voxy' / 'storage' / '00001.sst').exists())

    def test_copy_migrates_player_saveddata_and_config_names(self):
        with tempfile.TemporaryDirectory() as td:
            src, out = Path(td) / 'source', Path(td) / 'output'
            src.mkdir()
            (src / (OLD + '_sources.dat')).write_bytes(gzip.compress(compound(text_tag('id', OLD + ':lamp'))))
            (src / (OLD + '-client.toml')).write_text('lightingMode="Advanced"', encoding='utf-8')
            before = (src / (OLD + '_sources.dat')).read_bytes()
            result = m.migrate(src, out, OLD, NEW)
            self.assertEqual(result['status'], 'COMPLETE')
            self.assertTrue((out / (NEW + '-client.toml')).exists())
            self.assertIn(NEW.encode(), gzip.decompress((out / (NEW + '_sources.dat')).read_bytes()))
            self.assertEqual((src / (OLD + '_sources.dat')).read_bytes(), before)

    def test_refuses_existing_output_or_world_lock(self):
        with tempfile.TemporaryDirectory() as td:
            src = Path(td) / 'source'
            src.mkdir()
            with self.assertRaises(ValueError):
                m.migrate(src, src, OLD, NEW)
            (src / 'session.lock').write_bytes(b'locked')
            with self.assertRaisesRegex(ValueError, 'session.lock'):
                m.migrate(src, Path(td) / 'new', OLD, NEW)

    def test_unsupported_compression_marks_failed_and_preserves_source(self):
        with tempfile.TemporaryDirectory() as td:
            src, out = Path(td) / 'source', Path(td) / 'output'
            src.mkdir()
            raw, _ = region(compound(text_tag('id', OLD)), codec=2)
            raw = raw[:8196] + b'\x04' + raw[8197:]
            p = src / 'r.0.0.mca'
            p.write_bytes(raw)
            with self.assertRaisesRegex(ValueError, 'Unsupported'):
                m.migrate(src, out, OLD, NEW)
            self.assertEqual(p.read_bytes(), raw)
            self.assertIn('FAILED_DO_NOT_LOAD', (out / 'namespace-migration-report.json').read_text())


if __name__ == '__main__':
    unittest.main()
