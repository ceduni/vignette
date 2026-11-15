import {describe, expect, it} from 'vitest';
import {scenarioLocation} from '../../../src/utils/scenarioLocation';

describe('story locations on the globe', () => {
    const languages = {french: {latitude: 48.86, longitude: 2.35}};

    it('places a French story in Dakar at its story setting instead of France', () => {
        expect(scenarioLocation({languageId: 'french', location: {latitude: 14.72, longitude: -17.47}}, languages))
            .toEqual([14.72, -17.47]);
    });

    it('keeps language locations as a fallback for existing stories', () => {
        expect(scenarioLocation({languageId: 'french'}, languages)).toEqual([48.86, 2.35]);
        expect(scenarioLocation({languageId: 'missing'}, languages)).toBeNull();
    });

    it('accepts zero coordinates but ignores incomplete or out-of-range locations', () => {
        expect(scenarioLocation({location: {latitude: 0, longitude: 0}})).toEqual([0, 0]);
        for (const location of [{latitude: null, longitude: 0}, {latitude: 91, longitude: 0}, {latitude: 0, longitude: 181}]) {
            expect(scenarioLocation({languageId: 'french', location}, languages)).toEqual([48.86, 2.35]);
        }
    });
});
