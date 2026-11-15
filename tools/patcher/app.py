"""Three separately branded offline Windows patchers, sharing one audited engine."""
import argparse
import gzip
import json
from pathlib import Path
import queue
import sys
import threading
import traceback
import webbrowser
from engine import patch_pack


def bundle():
    root = Path(getattr(sys, "_MEIPASS", Path(__file__).parent))
    return json.loads(gzip.decompress((root / "payload.json.gz").read_bytes()))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    manifest = bundle()
    if args.input or args.output:
        try:
            if not args.input or not args.output:
                raise ValueError("Both --input and --output are required")
            result = patch_pack(args.input, args.output, manifest)
            code = 0
        except Exception as exc:
            result, code = {"success": False, "error": str(exc)}, 1
        if args.report:
            args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        if sys.stdout:
            print(json.dumps(result, ensure_ascii=False))
        return code

    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk
    root = tk.Tk()
    root.title(manifest["family"] + " + SLO Patch " + manifest["release"])
    root.geometry("780x570")
    root.minsize(700, 520)
    frame = ttk.Frame(root, padding=20)
    frame.pack(fill="both", expand=True)
    ttk.Label(frame, text=manifest["family"] + " + SLO Patch", font=("Segoe UI", 20, "bold")).pack(anchor="w")
    ttk.Label(frame, text="Offline local patcher / 离线本地补丁工具 · SLO " + manifest["mod_version"]).pack(anchor="w", pady=(2, 12))
    ttk.Label(frame, text="Download the original from its author first. No shader pack is bundled.\n请先从作者页面下载原包。本工具不附带完整光影包。", wraplength=720).pack(anchor="w")
    links = ttk.Frame(frame)
    links.pack(fill="x", pady=8)
    for label, url in manifest["links"]:
        ttk.Button(links, text=label, command=lambda u=url: webbrowser.open(u)).pack(side="left", padx=(0, 8))
    ttk.Label(frame, text="Supported / 支持：\n" + "\n".join(v["label"] for v in manifest["variants"]), wraplength=720).pack(anchor="w", pady=(0, 12))
    source = tk.StringVar()
    output = tk.StringVar()
    ttk.Label(frame, text="Original ZIP or extracted folder / 原包 ZIP 或解压目录").pack(anchor="w")
    ttk.Entry(frame, textvariable=source).pack(fill="x", pady=4)
    buttons = ttk.Frame(frame)
    buttons.pack(fill="x")

    def choose(folder=False):
        name = filedialog.askdirectory() if folder else filedialog.askopenfilename(filetypes=[("Shader ZIP", "*.zip")])
        if name:
            source.set(name)
            path = Path(name)
            output.set(str(path.parent / (path.stem + " + SLO Patch " + manifest["release"] + ".zip")))

    ttk.Button(buttons, text="Choose ZIP / 选择 ZIP", command=choose).pack(side="left")
    ttk.Button(buttons, text="Choose folder / 选择目录", command=lambda: choose(True)).pack(side="left", padx=8)
    ttk.Label(frame, text="New output ZIP / 新输出 ZIP（不覆盖原包）").pack(anchor="w", pady=(12, 0))
    ttk.Entry(frame, textvariable=output).pack(fill="x", pady=4)

    def save():
        name = filedialog.asksaveasfilename(defaultextension=".zip", filetypes=[("Shader ZIP", "*.zip")])
        if name:
            output.set(name)

    ttk.Button(frame, text="Save as… / 另存为…", command=save).pack(anchor="w")
    status = tk.StringVar(value="Ready / 就绪。原包校验通过后才会处理。")
    ttk.Label(frame, textvariable=status, wraplength=720).pack(anchor="w", pady=16)
    events = queue.Queue()
    busy = False

    def start():
        nonlocal busy
        if not source.get() or not output.get():
            messagebox.showerror("Missing path / 缺少路径", "请选择原包和输出位置。")
            return
        busy = True
        run.configure(state="disabled")
        src, dst = source.get(), output.get()

        def work():
            try:
                result = patch_pack(src, dst, manifest, lambda text: events.put(("status", text)))
                events.put(("done", result))
            except Exception as exc:
                events.put(("error", str(exc)))

        threading.Thread(target=work, daemon=True).start()

    run = ttk.Button(frame, text="Apply and verify / 打补丁并校验", command=start)
    run.pack(anchor="w")

    def poll():
        nonlocal busy
        while not events.empty():
            kind, value = events.get()
            if kind == "status":
                status.set(value)
            else:
                busy = False
                run.configure(state="normal")
                if kind == "done":
                    status.set("Success / 补丁成功，文件完整性校验通过。")
                    messagebox.showinfo("Success / 成功", value["output"] + "\n\n" +
                        "Put the ZIP in shaderpacks and select it in Iris.\n放入 shaderpacks，在 Iris 中选择新包。\n\n" +
                        "File verification passed; in-game GLSL compilation still depends on your setup.\n文件校验通过；实际 GLSL 编译仍需进游戏确认。")
                else:
                    status.set("Failed / 失败：" + value)
                    messagebox.showerror("Failed / 失败", value)
        root.after(100, poll)

    def close():
        if busy:
            messagebox.showinfo("Please wait / 请稍候", "Patching is in progress / 正在处理，请等待完成。")
        else:
            root.destroy()

    root.protocol("WM_DELETE_WINDOW", close)
    root.after(100, poll)
    root.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
