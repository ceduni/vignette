import {flushPromises, mount} from "@vue/test-utils";
import {beforeEach, describe, expect, it, vi} from "vitest";
import HomeView from "@/views/HomeView.vue";
import {fetchScenarios, fetchScenarioThumbnails} from "@/api/scenarios";

vi.mock("@/api/scenarios", () => ({
    fetchScenarios: vi.fn(),
    fetchScenarioThumbnails: vi.fn(),
}));

vi.mock("@/api/rest", () => ({
    buildApiUrl: vi.fn((path) => path),
}));

function mountHome() {
    return mount(HomeView, {
        global: {
            stubs: {
                GlobePolaroids: true,
                RouterLink: {
                    props: ["to"],
                    template: '<a :href="to"><slot /></a>',
                },
            },
        },
    });
}

describe("HomeView community scenarios", () => {
    beforeEach(() => {
        fetchScenarios.mockReset();
        fetchScenarioThumbnails.mockReset();
    });

    it("renders real published scenarios and links every card to its storyboard", async () => {
        fetchScenarios.mockResolvedValue([
            {
                id: 41,
                title: "Une soirée qui rassemble",
                authorUsername: "demo_amelie",
                languageId: "stan1290",
                visibilityStatus: "PUBLISHED",
            },
            {
                id: 42,
                title: "A Walk Through Shared Places",
                authorUsername: "demo_maya",
                languageId: "stan1293",
                visibilityStatus: "PUBLISHED",
            },
            {
                id: 99,
                title: "Private draft",
                authorUsername: "draft_author",
                languageId: "stan1293",
                visibilityStatus: "DRAFT",
            },
        ]);
        fetchScenarioThumbnails.mockImplementation(async (id) => (
            id === 41
                ? [{id: 501, idx: 1}, {id: 500, idx: 0}, {id: 502, idx: 2}]
                : [{id: 600, idx: 0}]
        ));

        const wrapper = mountHome();
        await flushPromises();

        const cards = wrapper.findAll(".home-card:not(.home-card--skel)");
        expect(cards).toHaveLength(2);
        expect(cards[0].attributes("href")).toBe("/scenarios/41");
        expect(cards[1].attributes("href")).toBe("/scenarios/42");
        expect(cards[0].text()).toContain("Une soirée qui rassemble");
        expect(cards[0].text()).toContain("demo_amelie");
        expect(cards[0].text()).toContain("3 scenes");
        expect(cards[0].find("img").attributes("src")).toBe("/api/thumbnails/500/content");
        expect(wrapper.text()).not.toContain("Private draft");
    });

    it("shows a useful catalogue link if the API cannot be reached", async () => {
        fetchScenarios.mockRejectedValue(new Error("Network unavailable"));

        const wrapper = mountHome();
        await flushPromises();

        expect(wrapper.text()).toContain("Couldn’t load community stories");
        expect(wrapper.text()).toContain("Network unavailable");
        expect(wrapper.get(".home-community__state-link").attributes("href")).toBe("/scenarios");
    });

    it("loads up to ten published scenarios into the horizontal feed", async () => {
        const published = Array.from({length: 12}, (_, index) => ({
            id: index + 1,
            title: `Community story ${index + 1}`,
            authorUsername: `author_${index + 1}`,
            languageId: "stan1293",
            visibilityStatus: "PUBLISHED",
        }));
        fetchScenarios.mockResolvedValue(published);
        fetchScenarioThumbnails.mockResolvedValue([{id: 700, idx: 0}]);

        const wrapper = mountHome();
        await flushPromises();

        expect(wrapper.findAll(".home-feed--scroll .home-card")).toHaveLength(10);
        expect(fetchScenarioThumbnails).toHaveBeenCalledTimes(10);
        expect(wrapper.get(".home-feed--scroll").attributes("aria-label")).toContain("Scroll horizontally");
    });
});
