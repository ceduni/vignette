import {mount} from "@vue/test-utils";
import AudioPanel from "@/components/AudioPanel.vue";

const scenarioApiMocks = vi.hoisted(() => ({
    uploadThumbnailAudio: vi.fn(),
}));

const toastMocks = vi.hoisted(() => ({
    success: vi.fn(),
    error: vi.fn(),
}));

vi.mock("@/api/scenarios", () => ({
    uploadThumbnailAudio: scenarioApiMocks.uploadThumbnailAudio,
}));

vi.mock("@/composables/useToast", () => ({
    useToast: () => ({
        success: toastMocks.success,
        error: toastMocks.error,
    }),
}));

vi.mock("@/components/ui/BaseBadge.vue", () => ({
    default: {
        name: "BaseBadge",
        template: "<span><slot /></span>",
    },
}));

vi.mock("@/components/community/DiscussionThread.vue", () => ({
    default: {
        name: "DiscussionThread",
        template: "<div class='discussion-thread-stub'>DiscussionThread</div>",
    },
}));

describe("AudioPanel", () => {
    const selectedThumb = {
        id: 10,
        idx: 2,
        title: "River",
    };

    const audios = [
        {
            id: 5,
            idx: 1,
            title: "Birds",
        },
    ];

    beforeEach(() => {
        scenarioApiMocks.uploadThumbnailAudio.mockReset();
        toastMocks.success.mockReset();
        toastMocks.error.mockReset();
    });

    function mountPanel(extraProps = {}) {
        return mount(AudioPanel, {
            props: {
                selectedThumb,
                audios,
                isOwner: false,
                activeAudioId: null,
                activeAudioTitle: "",
                playerState: "idle",
                ...extraProps,
            },
        });
    }

    it("is collapsed by default and opens on header click", async () => {
        const wrapper = mountPanel();

        expect(wrapper.find(".collapsible-card__body").exists()).toBe(false);

        await wrapper.find(".collapsible-card__header").trigger("click");

        expect(wrapper.find(".collapsible-card__body").exists()).toBe(true);
        expect(wrapper.text()).toContain("Existing audio clips");
    });

    it("emits play-audio when clicking play on a clip", async () => {
        const wrapper = mountPanel();

        await wrapper.find(".collapsible-card__header").trigger("click");
        const playButton = wrapper.findAll("button").find((b) => b.text() === "Play");
        await playButton.trigger("click");

        expect(wrapper.emitted("play-audio")).toEqual([[audios[0]]]);
    });

    it("shows read-only owner message for non-owners", async () => {
        const wrapper = mountPanel({isOwner: false});

        await wrapper.find(".collapsible-card__header").trigger("click");

        expect(wrapper.text()).toContain("only the owner can upload audio");
        expect(wrapper.text()).not.toContain("Start recording");
    });
});