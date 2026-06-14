package com.backrooms.graphics;

public final class ShaderSource {

    private ShaderSource() {}

    public static final String VERTEX_SHADER = """
        #version 330 core
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;

        uniform mat4 model;
        uniform mat4 view;
        uniform mat4 projection;

        out vec3 FragPos;
        out vec3 Normal;
        out vec2 TexCoord;

        void main() {
            vec4 worldPos = model * vec4(aPos, 1.0);
            FragPos = worldPos.xyz;
            Normal = mat3(transpose(inverse(model))) * aNormal;
            TexCoord = aTexCoord;
            gl_Position = projection * view * worldPos;
        }
        """;

    public static final String FRAGMENT_SHADER = """
        #version 330 core
        in vec3 FragPos;
        in vec3 Normal;
        in vec2 TexCoord;

        uniform vec3 viewPos;
        uniform vec4 wallColor;
        uniform vec3 ambientLight;
        uniform vec3 fogColor;
        uniform sampler2D wallTexture;
        uniform float useTexture;

        #define MAX_LIGHTS 64
        uniform int numLights;
        uniform vec3 lightPos[MAX_LIGHTS];
        uniform vec3 lightColor[MAX_LIGHTS];
        uniform float lightIntensity[MAX_LIGHTS];

        uniform float fogStart;
        uniform float fogEnd;

        out vec4 FragColor;

        void main() {
            vec3 N = gl_FrontFacing ? normalize(Normal) : -normalize(Normal);

            vec3 lighting = ambientLight;
            for (int i = 0; i < numLights && i < MAX_LIGHTS; i++) {
                vec3 lightDir = lightPos[i] - FragPos;
                float dist = length(lightDir);
                lightDir = lightDir / dist;

                float attenuation = lightIntensity[i] / (1.0 + 0.02 * dist + 0.0005 * dist * dist);

                float diff = max(dot(N, lightDir), 0.0);
                lighting += lightColor[i] * diff * attenuation;
            }

            vec3 color;
            if (useTexture > 0.5) {
                vec4 texColor = texture(wallTexture, TexCoord);
                color = texColor.rgb * wallColor.rgb;
            } else {
                color = wallColor.rgb;
            }

            float dist = length(viewPos - FragPos);
            float fogFactor = clamp((fogEnd - dist) / (fogEnd - fogStart), 0.0, 1.0);
            color = mix(fogColor, color * lighting, fogFactor);

            FragColor = vec4(color, 1.0);
        }
        """;

    public static final String OVERLAY_VERTEX = """
        #version 330 core
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        uniform mat4 model;
        out vec2 TexCoord;
        void main() {
            gl_Position = model * vec4(aPos, 1.0);
            TexCoord = aTexCoord;
        }
        """;

    public static final String OVERLAY_FRAGMENT = """
        #version 330 core
        in vec2 TexCoord;
        uniform vec4 uiColor;
        out vec4 FragColor;
        void main() {
            FragColor = uiColor;
        }
        """;
}
