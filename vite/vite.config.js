import {defineConfig} from "vitest/config";
import vue from "@vitejs/plugin-vue";
import {fileURLToPath, URL} from "node:url";

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            "@": fileURLToPath(new URL("./src", import.meta.url)),
        },
    },
    server: {
        proxy: {
            "/api": {
                target: "http://localhost:8081",
                changeOrigin: true,
            }
        }
    },
    test: {
        globals: true,
        environment: "jsdom",
        setupFiles: ["./tests/setup.js"],
        include: ["tests/unit/**/*.test.js"],
        css: true,
        clearMocks: true,
        restoreMocks: true,
        mockReset: true,
        coverage: {
            provider: "v8",
            reporter: ["text", "html"],
            include: ["src/**/*.{js,vue}"],
            exclude: ["src/main.js"],
        },
    },
});