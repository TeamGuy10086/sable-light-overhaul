# SLO smoke/fire lightmap tags. Restore illumination and preserve low-alpha density slices.
param([Parameter(Mandatory = $true)][string]$OutShaders)
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false)
$gas = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '..\src\main\resources\assets\sablelightoverhaul\shaders\include\slo_gas.glsl'),$utf8)
$smoke = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '..\src\main\resources\assets\sablelightoverhaul\shaders\include\slo_smoke.glsl'),$utf8)
$particle = 'defined PROGRAM_GBUFFERS_PARTICLES || defined PROGRAM_GBUFFERS_PARTICLES_TRANSLUCENT || defined PROGRAM_GBUFFERS_TEXTURED'
foreach ($program in @('gbuffers_all_solid', 'gbuffers_all_translucent')) {
    $rel = "program\$program.vsh"
    $text = [IO.File]::ReadAllText((Join-Path $PSScriptRoot "baselines\photon\shaders\$rel"),$utf8)
    $text = $text.Replace('out vec2 uv;', "out vec2 uv;`n#if $particle`nflat out int sloMuzzleKind;`nout vec3 sloGasPos;`nflat out vec4 sloGasParams;`n#endif")
    $anchor = '    material_mask = get_material_mask();'
    if ([regex]::Matches($text,[regex]::Escape($anchor)).Count -ne 1) { throw "muzzle vertex anchor mismatch: $rel" }
    $decode = @"

#if $particle
    int sloMuzzleRaw = int(floor(gl_MultiTexCoord1.x + 0.5)) & 65535;
    int sloMuzzleTag = sloMuzzleRaw & 15;
    int sloSkyRaw = int(floor(gl_MultiTexCoord1.y + 0.5)) & 65535;
    bool sloMaterialMarked = (sloSkyRaw & 15) == 11;
    sloMuzzleKind = sloMuzzleTag == 12 && sloMuzzleRaw > 255 ? 3 :
        (sloMaterialMarked ? (sloMuzzleTag == 13 ? 1 : (sloMuzzleTag == 14 ? 2 : (sloMuzzleTag == 15 ? 4 : (sloMuzzleTag == 11 ? 5 : (sloMuzzleTag == 10 ? 6 : (sloMuzzleTag == 9 ? 7 : 0)))))) : 0);
    sloGasPos = vec3(0.0);
    sloGasParams = vec4(0.0);
    if (sloMuzzleKind > 0) {
        light_levels.x = clamp01((gl_MultiTexCoord1.x - float(sloMuzzleTag)) / 240.0);
        light_levels.y = clamp01(float(sloSkyRaw & 65520) / 240.0);
    }
    if (sloMuzzleKind == 4) {
        float seed=floor((gl_MultiTexCoord0.x+1.5)/4.0);
        sloGasPos=vec3(gl_MultiTexCoord0.x-seed*4.0,gl_MultiTexCoord0.y,seed);
    }
    if (sloMuzzleKind == 5) sloGasPos=vec3(gl_MultiTexCoord0.xy,0.0);
    if (sloMuzzleKind == 3) {
        float q = float(sloMuzzleRaw >> 4) - 2048.0;
        sloGasPos = vec3(gl_MultiTexCoord0.xy,sign(q)*(exp2(abs(q)/128.0)-1.0));
        sloGasParams = vec4(gl_Color.r,exp2((gl_Color.g-0.5)*16.0),gl_Color.b,float(sloSkyRaw)*64.0/65535.0);
        light_levels = vec2(1.0);
        tint = vec4(1.0,1.0,1.0,gl_Color.a);
    }
#endif
"@
    $text = $text.Replace($anchor,$anchor + "`n" + $decode)
    $text = $text.Replace('    gl_Position = position_clip;', @"
    gl_Position = position_clip;
#if $particle
    // Keep world/view coordinates intact for cloud and LoD occlusion; clamp only raster depth.
    if(sloMuzzleKind==6 && gl_Position.w>0.0) gl_Position.z=min(gl_Position.z,gl_Position.w*0.999999);
#endif
"@)
    $last = $text.LastIndexOf("`n}")
    if ($last -lt 0) { throw "muzzle vertex closing brace missing: $rel" }
    $text = $text.Insert($last,"`n#if $particle`n    if (sloMuzzleKind == 1 || sloMuzzleKind == 3 || sloMuzzleKind == 6) material_mask = 32u; // flame emission, never smoke`n    if (sloMuzzleKind == 7) material_mask = 32u; // plasma`n    if (sloMuzzleKind == 2 || sloMuzzleKind == 4 || sloMuzzleKind == 5) material_mask = 0u; // smoke keeps native cooling light, no hardcoded emission`n#endif`n")
    # Articulated spotlight: emission marker is exclusive to the enabled lens.
    $lensMarker = @'
#if defined PROGRAM_GBUFFERS_BLOCK || defined PROGRAM_GBUFFERS_BLOCK_TRANSLUCENT
    if (int(floor(gl_MultiTexCoord1.x + 0.5)) == 247 && int(floor(gl_MultiTexCoord1.y + 0.5)) == 249) {
        material_mask = 32u; // SLO_LIT_LENS: full emission without a translucent overlay
        light_levels = vec2(1.0);
    }
#endif
'@
    $text = $text.Replace('material_mask = get_material_mask();', "material_mask = get_material_mask();`n" + $lensMarker)
    [IO.File]::WriteAllText((Join-Path $OutShaders $rel),$text,$utf8)
    $rel = "program\$program.fsh"
    $path = Join-Path $OutShaders $rel
    # Preserve the existing LOD edits in the generated translucent fragment shader.
    if (-not (Test-Path -LiteralPath $path)) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot "baselines\photon\shaders\$rel") -Destination $path }
    $text = if ($program -eq 'gbuffers_all_solid') {
        [IO.File]::ReadAllText((Join-Path $PSScriptRoot "baselines\photon\shaders\$rel"),$utf8)
    } else { [IO.File]::ReadAllText($path,$utf8) }
    if (-not $text.Contains('slo_muzzle_alpha_cutoff')) {
        $decl = @"
in vec2 uv;
#if $particle
flat in int sloMuzzleKind;
in vec3 sloGasPos;
flat in vec4 sloGasParams;
$gas
$smoke
#endif
float slo_muzzle_alpha_cutoff() {
#if $particle
    return sloMuzzleKind > 0 ? 0.002 : 0.1;
#else
    return 0.1;
#endif
}
"@
        $text = $text.Replace('in vec2 uv;',$decl)
        $text = $text.Replace('    vec4 base_color = read_tex(gtexture) * tint;', @"
    vec4 base_color = read_tex(gtexture) * tint;
#if $particle
    if (sloMuzzleKind == 3) base_color = slo_gas_volume(sloGasPos,sloGasParams,tint.a,scene_to_view_space(scene_pos),1e6);
    if (sloMuzzleKind == 4) {
        base_color = slo_smoke_tinted(sloGasPos.xy,round(sloGasPos.z),tint);
        if(abs(round(sloGasPos.z))>=128.0) base_color=slo_thermal_coverage(base_color);
    }
    if (sloMuzzleKind == 6) base_color=vec4(tint.rgb,1.0);
    if (sloMuzzleKind == 5) base_color = slo_ribbon_sample(sloGasPos.xy)*tint;
#endif
"@)
        $text = $text.Replace('        fragment_color = texture(gtexture, uv, lod_bias) * tint;', @"
        fragment_color = texture(gtexture, uv, lod_bias) * tint;
#if $particle
        if (sloMuzzleKind == 3) fragment_color = slo_gas_volume(sloGasPos,sloGasParams,tint.a,position_view,length(view_back_pos));
        if (sloMuzzleKind == 4) {
            fragment_color = slo_smoke_tinted(sloGasPos.xy,round(sloGasPos.z),tint);
            if(abs(round(sloGasPos.z))>=128.0) fragment_color=slo_thermal_coverage(fragment_color);
        }
        if (sloMuzzleKind == 6) fragment_color=vec4(tint.rgb,1.0);
        if (sloMuzzleKind == 5) fragment_color = slo_ribbon_sample(sloGasPos.xy)*tint;
#endif
"@)
        $text = $text.Replace('    // Diffuse lighting', @"
#if $particle
    if (sloMuzzleKind == 1 || sloMuzzleKind == 3 || sloMuzzleKind == 6) material.emission *= 0.35;
    // Diffuse lighting already multiplies the result by albedo.
    if (sloMuzzleKind == 7) material.emission = vec3(1.25);
    if (sloMuzzleKind == 4 && abs(round(sloGasPos.z)) >= 128.0) material.emission = vec3(2.2,0.72,0.12)*sloThermalHeat*sloSmokeOptions.w;
#endif
    // Diffuse lighting
"@)
        # Procedural gas carries no atlas normal/specular material. Preserve its explicit emission mask.
        $text = $text.Replace('decode_specular_map(specular_map, material);', @"
#if $particle
        if (sloMuzzleKind != 3 && sloMuzzleKind != 2 && sloMuzzleKind != 4 && sloMuzzleKind != 5 && sloMuzzleKind != 6 && sloMuzzleKind != 7)
#endif
        decode_specular_map(specular_map, material);
"@)

        foreach ($normalRead in @('vec3 normal_map = read_tex(normals).xyz;', 'vec3 normal_map = texture(normals, uv, lod_bias).xyz;')) {
            $text = $text.Replace($normalRead,$normalRead + "`n#if $particle`n    if(sloMuzzleKind==4 || sloMuzzleKind==5) normal_map=vec3(0.5,0.5,1.0);`n#endif")
        }
        $text = $text.Replace('vec4 specular_map = read_tex(specular);', 'vec4 specular_map = read_tex(specular);' + "`n#if $particle`n    if(sloMuzzleKind==4 || sloMuzzleKind==5) specular_map=vec4(0.0);`n#endif")
        $text = $text.Replace('base_color.a < 0.1','base_color.a < slo_muzzle_alpha_cutoff()')
        $text = $text.Replace('fragment_color.a < 0.1','fragment_color.a < slo_muzzle_alpha_cutoff()')
    }
    if (-not $text.Contains('< slo_muzzle_alpha_cutoff()')) { throw "muzzle fragment anchor mismatch: $rel" }
    if ($program -eq 'gbuffers_all_translucent') {
        $text = $text.Replace('float slo_z_fragment = screen_to_view_space_depth(gbufferProjectionInverse, depth0);',
            'float slo_z_fragment = -position_view.z; // actual depth, including far-plane-clamped tracers')
        $cloudSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'source\slo_particle_cloud.glsl'),$utf8)
        $text = $text.Replace('void main() {', $cloudSource + "`nvoid main() {")
        # Address Photon depth, LOD and clouds in full-resolution pixels during the reduced draw.
        $text = $text.Replace('gl_FragCoord.xy','slo_full_fragment_xy()')
        $fragmentCoordinates = @'
in vec2 uv;
uniform vec2 sloSmokePixelScale;
vec2 slo_full_fragment_xy() {
    return gl_FragCoord.xy * max(sloSmokePixelScale, vec2(1.0));
}
'@
        $text = $text.Replace('in vec2 uv;', $fragmentCoordinates)
        # Big smoke ends before material maps, PCSS and scene lights; same premultiplied fog contract.
        $fastAnchor = '        if (sloMuzzleKind == 5) fragment_color = slo_ribbon_sample(sloGasPos.xy)*tint;'
        if ([regex]::Matches($text,[regex]::Escape($fastAnchor)).Count -ne 1) { throw "thermal fast-path anchor mismatch" }
        $fastSmoke = @'
        float smokeSeed=round(sloGasPos.z);
        bool thermalSmoke=sloMuzzleKind==4 && abs(smokeSeed)>=128.0;
        bool cachedColdSmoke=sloMuzzleKind==4 && smokeSeed<=-64.0 && smokeSeed>-128.0;
        if((thermalSmoke && sloSmokeOptions.y>.5) || (cachedColdSmoke && sloCachedSmokeSimpleLighting!=0)) {
            if(fragment_color.a < slo_muzzle_alpha_cutoff()) discard;
            float sky=clamp(light_levels.y,0.0,1.0);
            vec3 environment=max(vec3(.001),ambient_color*(.2+.8*sky*sky)+light_color*(.10*sky*sky));
            if(cachedColdSmoke) environment+=vec3(1.0,.58,.28)*(.20*sqr(light_levels.x));
            vec3 smokeAlbedo=srgb_eotf_inv(fragment_color.rgb)*rec709_to_rec2020;
            vec3 radiance=smokeAlbedo*environment*sloSmokeOptions.z
                +smokeAlbedo*rcp_pi*vec3(2.2,.72,.12)*sloThermalHeat*emission_scale*sloSmokeOptions.w;
            vec4 smokeFog=common_fog(length(position_scene),false);
            radiance=purkinje_shift(radiance*smokeFog.a+smokeFog.rgb,light_levels);
            fragment_color.rgb=radiance*fragment_color.a;
            fragment_color = slo_apply_particle_cloud(fragment_color,coord,length(position_view),thermalSmoke);
            return;
        }
'@
        $text = $text.Replace($fastAnchor,$fastAnchor+"`n"+$fastSmoke.Replace("`r`n","`n"))
        $text = $text.Replace('    // Refraction data', @"
#if $particle
    if(sloMuzzleKind>0) {
        bool thermal = sloMuzzleKind==4 && abs(round(sloGasPos.z))>=128.0;
        fragment_color = slo_apply_particle_cloud(fragment_color,coord,length(position_view),thermal);
    }
#endif
    // Refraction data
"@)
        # PCSS returns zero outside its map/range and reports that through distance_fade.
        # The deferred opaque caller supplies distant lighting; the forward translucent
        # caller must do so too. Otherwise a camera-centred sphere cuts a dark disc in
        # large smoke billboards. Use the existing lightmap fallback, not another raymarch.
        $shadowAnchor = "        sss_depth`n    );`n#else`n#define sss_depth 0.0"
        if ([regex]::Matches($text,[regex]::Escape($shadowAnchor)).Count -ne 1) {
            throw "translucent shadow fallback anchor mismatch: $rel"
        }
        $shadowReplacement = @'
        sss_depth
    );
    // SLO: zero returned outside PCSS coverage is a missing sample, not an occluder.
    shadows = mix(shadows, vec3(get_lightmap_shadows(adjusted_light_levels.y)),
                  clamp01(shadow_distance_fade));
#else
#define sss_depth 0.0
'@
        $text = $text.Replace($shadowAnchor, $shadowReplacement.Replace("`r`n", "`n"))
        # RT13 is premultiplied. Soft smoke must fog/colour-shift its straight radiance,
        # then multiply the WHOLE result by coverage. Otherwise fog leaves finite RGB
        # at the alpha-discard boundary and the nonlinear night shift changes with alpha.
        # Scope to kind 4: preserve Photon's water/reflection and other particle contracts.
        $fogAnchor = '    fragment_color.rgb = fragment_color.rgb * fog.a + fog.rgb;'
        $shiftAnchor = '        purkinje_shift(fragment_color.rgb, adjusted_light_levels);'
        foreach ($anchor in @($fogAnchor, $shiftAnchor)) {
            if ([regex]::Matches($text,[regex]::Escape($anchor)).Count -ne 1) {
                throw "smoke edge fog/shift anchor mismatch: $rel"
            }
        }
        $edgeBegin = @"
#ifdef SLO_SMOKE_EDGE_FIX
#if $particle
    if (sloMuzzleKind == 4 || sloMuzzleKind == 5) {
        vec3 slo_smoke_radiance = fragment_color.rgb / max(fragment_color.a, 1e-6);
        slo_smoke_radiance = slo_smoke_radiance * fog.a + fog.rgb;
        fragment_color.rgb = purkinje_shift(slo_smoke_radiance, adjusted_light_levels)
                           * fragment_color.a;
    } else {
#endif
#endif
"@
        $text = $text.Replace($fogAnchor, $edgeBegin + "`n" + $fogAnchor)
        $text = $text.Replace($shiftAnchor, $shiftAnchor + "`n#ifdef SLO_SMOKE_EDGE_FIX`n#if $particle`n    }`n#endif`n#endif")
    }
    [IO.File]::WriteAllText($path,$text,$utf8)
}
Write-Output 'muzzle smoke/fire: tags 11/12/13/14/15, shared 3D gas field, restored lighting, soft alpha cutoff, flame emission'
