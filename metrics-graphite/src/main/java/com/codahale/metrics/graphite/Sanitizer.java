package com.codahale.metrics.graphite;

import java.util.regex.Pattern;

final class Sanitizer {

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[^a-z0-9!#\\$%&\"'\\*\\+\\-.:;<=>\\?@\\[\\]\\^_`\\|~]+", Pattern.CASE_INSENSITIVE);

    public static final String sanitize(String name) {
        return ILLEGAL_CHARS.matcher(name).replaceAll("-");
    }

}
