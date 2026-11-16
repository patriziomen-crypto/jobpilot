package it.patrizio.jobpilot.jobs;

/** Da dove arriva un'offerta di lavoro. */
public enum JobSource {
    LINKEDIN,
    INDEED,
    ALTRO;

    /** Riconosce la fonte dall'URL; ALTRO se non è né LinkedIn né Indeed. */
    public static JobSource fromUrl(String url) {
        if (url == null) {
            return ALTRO;
        }
        String u = url.toLowerCase();
        if (u.contains("linkedin.com")) {
            return LINKEDIN;
        }
        if (u.contains("indeed.com") || u.contains("indeed.it")) {
            return INDEED;
        }
        return ALTRO;
    }
}
