import {mount} from "@vue/test-utils";
import ThumbnailCard from "@/components/ThumbnailCard.vue";

describe("ThumbnailCard", () => {
    const thumb = {
        id: 12,
        idx: 3,
        title: "Forest scene",
    };

    it("emits select when card is clicked", async () => {
        const wrapper = mount(ThumbnailCard, {
            props: {
                thumb,
                audios: [],
            },
        });

        await wrapper.find("article").trigger("click");

        expect(wrapper.emitted("select")).toEqual([[thumb]]);
    });

    it("asks for confirmation and emits delete when confirmed", async () => {
        const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);

        const wrapper = mount(ThumbnailCard, {
            props: {
                thumb,
                audios: [],
                canDelete: true,
            },
        });

        await wrapper.find(".storyboard-tile__delete").trigger("click");

        expect(confirmSpy).toHaveBeenCalled();
        expect(wrapper.emitted("delete")).toEqual([[thumb]]);

        confirmSpy.mockRestore();
    });

    it("does not emit delete when confirmation is declined", async () => {
        const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);

        const wrapper = mount(ThumbnailCard, {
            props: {
                thumb,
                audios: [],
                canDelete: true,
            },
        });

        await wrapper.find(".storyboard-tile__delete").trigger("click");

        expect(confirmSpy).toHaveBeenCalled();
        expect(wrapper.emitted("delete")).toBeUndefined();

        confirmSpy.mockRestore();
    });

    it("shows direct move and resize controls for editable storyboards", async () => {
        const wrapper = mount(ThumbnailCard, {
            props: {
                thumb,
                audios: [{id: 8}],
                canReorder: true,
                canResize: true,
                colSpan: 6,
                rowSpan: 4,
            },
        });

        expect(wrapper.find(".storyboard-tile__drag-handle").attributes("title")).toContain("move");
        expect(wrapper.find(".storyboard-tile__size-badge").text()).toBe("6 × 4");
        expect(wrapper.findAll(".storyboard-tile__handle")).toHaveLength(3);
        expect(wrapper.find(".storyboard-tile__caption").text()).toContain("1 take");

        await wrapper.find(".storyboard-tile__handle--corner").trigger("pointerdown");
        expect(wrapper.emitted("resize-start")).toHaveLength(1);
        expect(wrapper.emitted("resize-start")[0][0].direction).toBe("corner");
    });
});
