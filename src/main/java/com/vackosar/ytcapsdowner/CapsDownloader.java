package com.vackosar.ytcapsdowner;

import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

public class CapsDownloader {

    private static final int MAX_RETRY = 2;
    private static final String VIDEO_INFO_PREFIX = "http://www.youtube.com/get_video_info?video_id=";
    private static final String CAPTIONS_TOKEN = "caption_tracks";
    private static final String URL_TOKEN = "u";
    private static final String LANG_TOKEN = "lang";
    private static final String EN_LANG_TOKEN_VALUE = "en";
    private static final String QUERY_SEPARATOR = "([&?])";
    private static final String QUERY_VALUE_SEPARATOR = "=[^&]*";
    private static final String FIRST_MATCH = "$1";
    private static final String EQUALS = "=";
    private static final String SLASH_PREFIX = "^/";
    private static final String EMPTY = "";
    private static final String DESKTOP_URL = "www.youtube.com";
    private static final String MOBILE_URL = "m.youtube.com";
    private static final String SHORT_URL = "youtu.be";
    private static final String TITLE_TOKEN = "title";

    public Result download(String uri) {
        Exception exception = null;
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                String videoInfo = convertStreamToString(createVideoInfoUrl(uri).openConnection().getInputStream());
                String title = extractTokenValue(TITLE_TOKEN, videoInfo);
                String captionTracks = extractTokenValue(CAPTIONS_TOKEN, videoInfo);
                String url = extractTokenValue(URL_TOKEN, captionTracks);
                String englishUrl = setTokenValue(LANG_TOKEN, EN_LANG_TOKEN_VALUE, url);
                String subtitles = convertStreamToString(new URL(englishUrl).openConnection().getInputStream());
                String text = extractText(subtitles);
                return new Result(title, text);
            } catch (TokenNotFound | IOException ignored) {
                exception = ignored;
            }
        }
        throw new IllegalArgumentException("English captions couldn't be downloaded for URL: '" + uri + "'.\nError message: " + exception.getMessage());
    }

    private String setTokenValue(String key, String value, String url) {
        return url.replaceFirst(QUERY_SEPARATOR + key + QUERY_VALUE_SEPARATOR, FIRST_MATCH + key + EQUALS + value);
    }

    private URL createVideoInfoUrl(String urlString) throws MalformedURLException {
        if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
            urlString = "https://" + urlString;
        }
        URL url = new URL(urlString);
        if (DESKTOP_URL.equals(url.getHost()) || MOBILE_URL.equals(url.getHost())) {
            for (String tuple: url.getQuery().split("&")) {
                if (tuple.startsWith("v=")) {
                    String value = tuple.replaceAll("v=", "");
                    return new URL(VIDEO_INFO_PREFIX + value);
                }
            }
        } else if (SHORT_URL.equals(url.getHost())) {
            return new URL(VIDEO_INFO_PREFIX + url.getPath().replaceFirst(SLASH_PREFIX, EMPTY));
        } else {
            throw new IllegalArgumentException("Invalid Youtube address.");
        }
        throw new RuntimeException("Could not parse exception.");
    }

    private String extractText(String subs) {
        return StringEscapeUtils.unescapeHtml(subs.replaceAll("<[^>]*>", " "))
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[‘’]", "'")
                .replaceAll("&#39;", "'")
                .replaceAll("\n", " ")
                .replaceAll(" +", " ");
    }

    private String extractTokenValue(String name, String tokens) throws UnsupportedEncodingException {
        for (String token: tokens.split("&")) {
            if (token.startsWith(name + EQUALS)) {
                return URLDecoder.decode(token.split(EQUALS)[1], "UTF-8");
            }
        }
        throw new TokenNotFound("Token " + name + " not found in " + tokens);
    }

    private static String convertStreamToString(java.io.InputStream is) throws IOException {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        String output = s.hasNext() ? s.next() : "";
        is.close();
        return output;
    }

    private static class TokenNotFound extends IllegalArgumentException {
        TokenNotFound(String msg) { super(msg); }
    }

    public static class Result {

        public final String title;
        public final String text;

        public Result(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

}
