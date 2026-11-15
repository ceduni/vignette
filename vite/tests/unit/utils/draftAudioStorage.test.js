import {draftAudioStudioPath} from "@/utils/draftAudioStorage";

describe("draftAudioStudioPath", () => {
    it("opens the creation flow for a signed-in user", () => {
        expect(draftAudioStudioPath({id: "draft-1", title: "Family story"}, "lea"))
            .toBe("/create-scenario?draftAudio=draft-1&draftTitle=Family+story");
    });

    it("opens the temporary studio for an anonymous user", () => {
        expect(draftAudioStudioPath({id: "draft-2"}, null))
            .toBe("/scenarios/emergency-draft-2?draftAudio=draft-2");
    });
});
