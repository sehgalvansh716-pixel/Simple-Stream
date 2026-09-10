package com.lagradost.cloudstream3.ui.utils

/**
 * Universal shader definitions for SimpleStream dynamic ambient theme backgrounds.
 *
 * Provides mathematically identical shader sources for:
 * 1. Android 13+ (API 33+) AGSL (Android Graphics Shading Language via RuntimeShader)
 * 2. Android < 33 OpenGL ES 2.0 Fragment Shaders
 *
 * All 7 themes feature 4-octave simplex FBM wave dynamics, ribbon/ridge formulas,
 * atmospheric vignettes, and calibrated theme color palettes.
 */
object ThemeShaderCode {

    const val DEFAULT_THEME = "MidnightMonochrome"

    fun normalizeThemeKey(themeKey: String?): String {
        return when (themeKey) {
            "MidnightMonochrome" -> "MidnightMonochrome"
            "WarmMinimal" -> "WarmMinimal"
            "SageCream" -> "SageCream"
            "ArcticGlass" -> "ArcticGlass"
            "SoftLavender" -> "SoftLavender"
            "ObsidianElectric" -> "ObsidianElectric"
            "PureMono" -> "PureMono"
            "Lavender" -> "SoftLavender"
            "SilentBlue" -> "ArcticGlass"
            "Light", "System" -> "PureMono"
            else -> "MidnightMonochrome"
        }
    }

    private const val COMMON_NOISE_FUNCTIONS = """
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec2 mod289(vec2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec3 permute(vec3 x) { return mod289(((x * 34.0) + 1.0) * x); }

float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
    vec2 i  = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = vec4(x0.xy - i1 + C.xx, x0.xy + C.zz);
    i = mod289(i);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m; m = m * m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 ox = floor(x + 0.5);
    vec3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.y = a0.y * x12.x + h.y * x12.y;
    g.z = a0.z * x12.z + h.z * x12.w;
    return 130.0 * dot(m, g);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * snoise(p);
        p *= 2.04;
        a *= 0.5;
    }
    return v;
}
"""

    private fun getThemeCalculations(themeKey: String): String {
        return when (normalizeThemeKey(themeKey)) {
            "MidnightMonochrome" -> """
    float t = u_time * 0.16;
    vec2 drift = vec2(sin(t * 0.35) * 0.3, cos(t * 0.28) * 0.3);
    p += drift;

    vec2 q = vec2(
        fbm(p * 1.5 + vec2(0.0, 0.0) + vec2(t * 0.35, t * 0.25)),
        fbm(p * 1.5 + vec2(5.2, 1.3) + vec2(-t * 0.3, t * 0.38))
    );

    vec2 r = vec2(
        fbm(p * 1.7 + 3.2 * q + vec2(1.7, 9.2) + vec2(t * 0.4, -t * 0.32)),
        fbm(p * 1.7 + 3.2 * q + vec2(8.3, 2.8) + vec2(-t * 0.35, t * 0.45))
    );

    float f = fbm(p * 1.3 + 3.4 * r + vec2(t * 0.25, t * 0.35));

    float ridge = max(1.0 - abs(f), 0.0);
    ridge = pow(ridge, 2.6);

    float ribbon1 = smoothstep(0.1, 0.85, sin(p.y * 2.4 + f * 3.6 + t * 0.8) * 0.5 + 0.5);
    float ribbon2 = smoothstep(0.2, 0.9, cos(p.x * 1.9 - f * 2.8 + t * 0.6) * 0.5 + 0.5);
    float waveMix = pow(max(ribbon1 * ribbon2, 0.0), 1.4);

    vec3 bg = vec3(0.043, 0.043, 0.051);       // #0B0B0D
    vec3 surface = vec3(0.082, 0.082, 0.094);  // #151518
    vec3 secondary = vec3(0.541, 0.541, 0.573);// #8A8A92
    vec3 accent = vec3(1.0, 1.0, 1.0);         // #FFFFFF

    vec3 col = mix(bg, surface, clamp(length(q) * 0.8, 0.0, 1.0) * 0.7);
    vec3 accentBlend = mix(secondary, accent, clamp(r.x * 0.6 + 0.4, 0.0, 1.0));
    col = mix(col, accentBlend, waveMix * 0.55);
    col += accent * (pow(ridge, 3.4) * 0.38);

    float vig = 1.0 - smoothstep(0.4, 1.4, length(uv - 0.5) * 1.3);
    col *= mix(0.85, 1.0, vig);
    float grain = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    col += vec3((grain - 0.5) * 0.012);
"""
            "WarmMinimal" -> """
    float t = u_time * 0.15;
    vec2 drift = vec2(sin(t * 0.32) * 0.28, cos(t * 0.26) * 0.28);
    p += drift;

    vec2 q = vec2(
        fbm(p * 1.45 + vec2(0.0, 0.0) + vec2(t * 0.32, t * 0.22)),
        fbm(p * 1.45 + vec2(5.2, 1.3) + vec2(-t * 0.28, t * 0.35))
    );

    vec2 r = vec2(
        fbm(p * 1.65 + 3.2 * q + vec2(1.7, 9.2) + vec2(t * 0.38, -t * 0.3)),
        fbm(p * 1.65 + 3.2 * q + vec2(8.3, 2.8) + vec2(-t * 0.32, t * 0.42))
    );

    float f = fbm(p * 1.25 + 3.4 * r + vec2(t * 0.22, t * 0.32));

    float ridge = max(1.0 - abs(f), 0.0);
    ridge = pow(ridge, 2.4);

    float ribbon1 = smoothstep(0.1, 0.85, sin(p.y * 2.3 + f * 3.4 + t * 0.75) * 0.5 + 0.5);
    float ribbon2 = smoothstep(0.2, 0.9, cos(p.x * 1.8 - f * 2.6 + t * 0.55) * 0.5 + 0.5);
    float waveMix = pow(max(ribbon1 * ribbon2, 0.0), 1.35);

    vec3 bg = vec3(0.961, 0.945, 0.910);        // #F5F1E8
    vec3 surface = vec3(1.0, 0.988, 0.961);     // #FFFCF5
    vec3 shadow = vec3(0.855, 0.812, 0.745);    // deep warm taupe depth
    vec3 accent = vec3(0.722, 0.541, 0.353);    // #B88A5A rich caramel
    vec3 accentGlint = vec3(0.875, 0.733, 0.588);

    vec3 col = mix(bg, surface, clamp(length(q) * 0.7, 0.0, 1.0));
    col = mix(col, shadow, (1.0 - ridge) * 0.32);

    vec3 ribbonColor = mix(accent, accentGlint, clamp(r.x * 0.5 + 0.5, 0.0, 1.0));
    col = mix(col, ribbonColor, waveMix * 0.52);
    col += accentGlint * (pow(ridge, 3.8) * 0.28);

    float grain = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    col += vec3((grain - 0.5) * 0.009);
"""
            "SageCream" -> """
    float t = u_time * 0.15;
    vec2 drift = vec2(sin(t * 0.3) * 0.26, cos(t * 0.25) * 0.26);
    p += drift;

    vec2 q = vec2(
        fbm(p * 1.5 + vec2(0.0, 0.0) + vec2(t * 0.3, t * 0.22)),
        fbm(p * 1.5 + vec2(5.2, 1.3) + vec2(-t * 0.26, t * 0.34))
    );

    vec2 r = vec2(
        fbm(p * 1.7 + 3.2 * q + vec2(1.7, 9.2) + vec2(t * 0.36, -t * 0.28)),
        fbm(p * 1.7 + 3.2 * q + vec2(8.3, 2.8) + vec2(-t * 0.3, t * 0.4))
    );

    float f = fbm(p * 1.3 + 3.4 * r + vec2(t * 0.22, t * 0.32));

    float ridge = max(1.0 - abs(f), 0.0);
    ridge = pow(ridge, 2.5);

    float ribbon1 = smoothstep(0.1, 0.85, sin(p.y * 2.3 + f * 3.5 + t * 0.75) * 0.5 + 0.5);
    float ribbon2 = smoothstep(0.2, 0.9, cos(p.x * 1.8 - f * 2.7 + t * 0.58) * 0.5 + 0.5);
    float waveMix = pow(max(ribbon1 * ribbon2, 0.0), 1.35);

    vec3 bg = vec3(0.953, 0.957, 0.933);        // #F3F4EE
    vec3 surface = vec3(1.0, 1.0, 1.0);         // #FFFFFF
    vec3 shadow = vec3(0.835, 0.863, 0.812);    // cool sage depth
    vec3 sageAccent = vec3(0.529, 0.612, 0.529);// #879C87
    vec3 lightSage = vec3(0.706, 0.784, 0.706);

    vec3 col = mix(bg, surface, clamp(length(q) * 0.75, 0.0, 1.0));
    col = mix(col, shadow, (1.0 - ridge) * 0.3);

    vec3 ribbonColor = mix(sageAccent, lightSage, clamp(r.x * 0.5 + 0.5, 0.0, 1.0));
    col = mix(col, ribbonColor, waveMix * 0.5);
    col += lightSage * (pow(ridge, 3.6) * 0.26);

    float grain = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    col += vec3((grain - 0.5) * 0.009);
"""
            "ArcticGlass" -> """
    float t = u_time * 0.17;
    vec2 drift = vec2(sin(t * 0.35) * 0.3, cos(t * 0.28) * 0.3);
    p += drift;

    vec2 q = vec2(
        fbm(p * 1.55 + vec2(0.0, 0.0) + vec2(t * 0.35, t * 0.24)),
        fbm(p * 1.55 + vec2(5.2, 1.3) + vec2(-t * 0.3, t * 0.36))
    );

    vec2 r = vec2(
        fbm(p * 1.75 + 3.2 * q + vec2(1.7, 9.2) + vec2(t * 0.4, -t * 0.3)),
        fbm(p * 1.75 + 3.2 * q + vec2(8.3, 2.8) + vec2(-t * 0.34, t * 0.44))
    );

    float f = fbm(p * 1.3 + 3.4 * r + vec2(t * 0.24, t * 0.34));

    float ridge = max(1.0 - abs(f), 0.0);
    ridge = pow(ridge, 2.5);

    float ribbon1 = smoothstep(0.1, 0.85, sin(p.y * 2.4 + f * 3.5 + t * 0.8) * 0.5 + 0.5);
    float ribbon2 = smoothstep(0.2, 0.9, cos(p.x * 1.9 - f * 2.8 + t * 0.6) * 0.5 + 0.5);
    float waveMix = pow(max(ribbon1 * ribbon2, 0.0), 1.35);

    vec3 bg = vec3(0.969, 0.976, 0.988);        // #F7F9FC
    vec3 surface = vec3(1.0, 1.0, 1.0);         // #FFFFFF
    vec3 frostShadow = vec3(0.835, 0.882, 0.957);// deep glacial depth
    vec3 iceBlue = vec3(0.486, 0.624, 1.0);     // #7C9FFF
    vec3 lightCyan = vec3(0.745, 0.855, 1.0);

    vec3 col = mix(bg, surface, clamp(length(q) * 0.8, 0.0, 1.0));
    col = mix(col, frostShadow, (1.0 - ridge) * 0.35);

    vec3 ribbonColor = mix(iceBlue, lightCyan, clamp(r.x * 0.6 + 0.4, 0.0, 1.0));
    col = mix(col, ribbonColor, waveMix * 0.54);
    col += lightCyan * (pow(ridge, 3.8) * 0.35);

    float grain = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    col += vec3((grain - 0.5) * 0.009);
"""
            "SoftLavender" -> """
    float t = u_time * 0.16;
    vec2 drift = vec2(sin(t * 0.32) * 0.28, cos(t * 0.26) * 0.28);
    p += drift;

    vec2 q = vec2(
        fbm(p * 1.5 + vec2(0.0, 0.0) + vec2(t * 0.32, t * 0.24)),
        fbm(p * 1.5 + vec2(5.2, 1.3) + vec2(-t * 0.28, t * 0.36))
    );

    vec2 r = vec2(
        fbm(p * 1.7 + 3.2 * q + vec2(1.7, 9.2) + vec2(t * 0.38, -t * 0.3)),
        fbm(p * 1.7 + 3.2 * q + vec2(8.3, 2.8) + vec2(-t * 0.32, t * 0.44))
    );

    float f = fbm(p * 1.3 + 3.4 * r + vec2(t * 0.24, t * 0.32));

    float ridge = max(1.0 - abs(f), 0.0);
    ridge = pow(ridge, 2.5);

    float ribbon1 = smoothstep(0.1, 0.85, sin(p.y * 2.3 + f * 3.5 + t * 0.78) * 0.5 + 0.5);
    float ribbon2 = smoothstep(0.2, 0.9, cos(p.x * 1.8 - f * 2.7 + t * 0.58) * 0.5 + 0.5);
    float waveMix = pow(max(ribbon1 * ribbon2, 0.0), 1.35);

    vec3 bg = vec3(0.969, 0.961, 0.980);        // #F7F5FA
    vec3 surface = vec3(1.0, 1.0, 1.0);         // #FFFFFF
    vec3 shadow = vec3(0.867, 0.835, 0.914);    // deep amethyst depth
    vec3 lavender = vec3(0.659, 0.584, 0.835);  // #A895D5
    vec3 lightLilac = vec3(0.827, 0.776, 0.941);

    vec3 col = mix(bg, surface, clamp(length(q) * 0.75, 0.0, 1.0));
    col = mix(col, shadow, (1.0 - ridge) * 0.32);

    vec3 ribbonColor = mix(lavender, lightLilac, clamp(r.x * 0.55 + 0.45, 0.0, 1.0));
    col = mix(col, ribbonColor, waveMix * 0.52);
    col += lightLilac * (pow(ridge, 3.8) * 0.28);

    float grain = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    col += vec3((grain - 0.5) * 0.009);
"""
            "ObsidianElectric" -> """
    float t = u_time * 0.18;
    vec2 drift = vec2(sin(t * 0.36) * 0.32, cos(t * 0.3) * 0.32);
    p += drift;

    vec2 q = vec2(
        fbm(p * 1.55 + vec2(0.0, 0.0) + vec2(t * 0.36, t * 0.26)),
        fbm(p * 1.55 + vec2(5.2, 1.3) + vec2(-t * 0.32, t * 0.4))
    );

    vec2 r = vec2(
        fbm(p * 1.75 + 3.2 * q + vec2(1.7, 9.2) + vec2(t * 0.42, -t * 0.34)),
        fbm(p * 1.75 + 3.2 * q + vec2(8.3, 2.8) + vec2(-t * 0.36, t * 0.46))
    );

    float f = fbm(p * 1.3 + 3.4 * r + vec2(t * 0.26, t * 0.36));

    float ridge = max(1.0 - abs(f), 0.0);
    ridge = pow(ridge, 2.6);

    float ribbon1 = smoothstep(0.1, 0.85, sin(p.y * 2.4 + f * 3.6 + t * 0.82) * 0.5 + 0.5);
    float ribbon2 = smoothstep(0.2, 0.9, cos(p.x * 1.9 - f * 2.8 + t * 0.62) * 0.5 + 0.5);
    float waveMix = pow(max(ribbon1 * ribbon2, 0.0), 1.4);

    vec3 bg = vec3(0.035, 0.039, 0.047);        // #090A0C
    vec3 surface = vec3(0.067, 0.075, 0.094);   // #111318
    vec3 electricGreen = vec3(0.486, 1.0, 0.698);// #7CFFB2
    vec3 electricBlue = vec3(0.486, 0.655, 1.0); // #7CA7FF

    vec3 col = mix(bg, surface, clamp(length(q) * 0.8, 0.0, 1.0) * 0.7);
    vec3 neon = mix(electricGreen, electricBlue, clamp(r.x * 0.6 + 0.4, 0.0, 1.0));
    col = mix(col, neon, waveMix * 0.65);
    col += neon * (pow(ridge, 3.2) * 0.6);

    float vig = 1.0 - smoothstep(0.4, 1.45, length(uv - 0.5) * 1.35);
    col *= mix(0.82, 1.0, vig);
    float grain = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    col += vec3((grain - 0.5) * 0.015);
"""
            "PureMono" -> """
    float t = u_time * 0.16;
    vec2 drift = vec2(sin(t * 0.32) * 0.3, cos(t * 0.26) * 0.3);
    p += drift;

    vec2 q = vec2(
        fbm(p * 1.5 + vec2(0.0, 0.0) + vec2(t * 0.34, t * 0.24)),
        fbm(p * 1.5 + vec2(5.2, 1.3) + vec2(-t * 0.3, t * 0.36))
    );

    vec2 r = vec2(
        fbm(p * 1.7 + 3.2 * q + vec2(1.7, 9.2) + vec2(t * 0.38, -t * 0.32)),
        fbm(p * 1.7 + 3.2 * q + vec2(8.3, 2.8) + vec2(-t * 0.34, t * 0.44))
    );

    float f = fbm(p * 1.3 + 3.4 * r + vec2(t * 0.24, t * 0.34));

    float ridge = max(1.0 - abs(f), 0.0);
    ridge = pow(ridge, 2.6);

    float ribbon1 = smoothstep(0.1, 0.85, sin(p.y * 2.3 + f * 3.5 + t * 0.78) * 0.5 + 0.5);
    float ribbon2 = smoothstep(0.2, 0.9, cos(p.x * 1.9 - f * 2.7 + t * 0.58) * 0.5 + 0.5);
    float waveMix = pow(max(ribbon1 * ribbon2, 0.0), 1.35);

    float lum = 1.0;
    lum -= clamp(length(q) * 0.4, 0.0, 1.0) * 0.18;
    lum -= waveMix * 0.55;
    lum -= pow(ridge, 3.2) * 0.35;
    lum = clamp(lum, 0.04, 1.0);

    float grain = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    lum += (grain - 0.5) * 0.008;

    vec3 col = vec3(lum);
"""
            else -> getThemeCalculations(DEFAULT_THEME)
        }
    }

    /**
     * Builds an Android 13+ (API 33+) AGSL SkSL shader string.
     */
    fun getAgslShader(themeKey: String): String {
        val calculations = getThemeCalculations(themeKey)
        return """
uniform float2 u_res;
uniform float u_time;

$COMMON_NOISE_FUNCTIONS

half4 main(float2 fragCoord) {
    vec2 coord = vec2(fragCoord.x, u_res.y - fragCoord.y);
    vec2 uv = coord / u_res;
    vec2 p = (coord - 0.5 * u_res) / min(u_res.y, u_res.x);

$calculations

    return half4(col, 1.0);
}
""".trimIndent()
    }

    /**
     * Builds an OpenGL ES 2.0 Fragment Shader string.
     */
    fun getGlslFragmentShader(themeKey: String): String {
        val calculations = getThemeCalculations(themeKey)
        return """
precision highp float;
uniform vec2 u_res;
uniform float u_time;

$COMMON_NOISE_FUNCTIONS

void main() {
    vec2 coord = gl_FragCoord.xy;
    vec2 uv = coord / u_res;
    vec2 p = (coord - 0.5 * u_res) / min(u_res.y, u_res.x);

$calculations

    gl_FragColor = vec4(col, 1.0);
}
""".trimIndent()
    }

    /**
     * OpenGL ES 2.0 Full-Screen Quad Vertex Shader.
     */
    const val GLSL_VERTEX_SHADER = """
attribute vec2 p;
void main() {
    gl_Position = vec4(p, 0.0, 1.0);
}
"""
}
