import "@testing-library/jest-dom/vitest";

Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
});

class ResizeObserverMock {
    observe() {
    }

    unobserve() {
    }

    disconnect() {
    }
}

global.ResizeObserver = ResizeObserverMock;

Object.defineProperty(window, "scrollTo", {
    writable: true,
    value: vi.fn(),
});

const createObjectURL = vi.fn(() => "blob:mock-url");
const revokeObjectURL = vi.fn();

globalThis.URL.createObjectURL = globalThis.URL.createObjectURL || createObjectURL;
globalThis.URL.revokeObjectURL = globalThis.URL.revokeObjectURL || revokeObjectURL;
window.URL.createObjectURL = window.URL.createObjectURL || createObjectURL;
window.URL.revokeObjectURL = window.URL.revokeObjectURL || revokeObjectURL;

beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
});
