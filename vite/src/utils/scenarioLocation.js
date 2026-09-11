function coordinates(location) {
    const latitude = location?.latitude;
    const longitude = location?.longitude;
    return Number.isFinite(latitude) && Number.isFinite(longitude)
        && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
        ? [latitude, longitude] : null;
}

export function scenarioLocation(scenario, languageMap = {}) {
    return coordinates(scenario?.location)
        ?? coordinates(languageMap[String(scenario?.languageId)]);
}
