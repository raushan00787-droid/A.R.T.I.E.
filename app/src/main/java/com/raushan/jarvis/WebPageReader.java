package com.raushan.jarvis;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

/**
 * Bounded reader for a single user-supplied public webpage.
 * It intentionally does not execute scripts, follow arbitrary redirects, or fetch linked pages.
 */
final class WebPageReader {
    private static final int MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 16000;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_REDIRECTS = 3;

    static final class Page {
        final String url;
        final String title;
        final String description;
        final String text;

        Page(String url, String title, String description, String text) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.text = text;
        }
    }

    private WebPageReader() { }

    static Page read(Context context, String rawUrl) throws Exception {
        String current = normalize(rawUrl);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            URI uri = validatePublicHttpUrl(current);
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(current).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "ARTIE-Android/1.0 (user-requested webpage reader)");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain,application/pdf;q=0.9");
                connection.setRequestProperty("Accept-Encoding", "identity");

                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_SEE_OTHER
                        || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirect == MAX_REDIRECTS) {
                        throw new Exception("The page redirected too many times or omitted its destination.");
                    }
                    URL next = new URL(new URL(current), location);
                    String nextUrl = next.toExternalForm();
                    URI nextUri = validatePublicHttpUrl(nextUrl);
                    if ("https".equalsIgnoreCase(uri.getScheme())
                            && "http".equalsIgnoreCase(nextUri.getScheme())) {
                        throw new Exception("The page attempted to downgrade from HTTPS to HTTP.");
                    }
                    current = nextUrl;
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new Exception("The webpage returned HTTP " + status + ".");
                }

                String type = connection.getContentType();
                String lowerType = type == null ? "" : type.toLowerCase(Locale.ROOT);
                boolean pdf = lowerType.contains("application/pdf")
                        || (connection.getURL().getPath() != null
                        && connection.getURL().getPath().toLowerCase(Locale.ROOT).endsWith(".pdf"));
                if (!(pdf || lowerType.contains("text/html")
                        || lowerType.contains("application/xhtml+xml")
                        || lowerType.startsWith("text/plain"))) {
                    throw new Exception("This link is not a supported HTTPS webpage or PDF document.");
                }
                long declaredLength = connection.getContentLengthLong();
                if (declaredLength > MAX_DOWNLOAD_BYTES) {
                    throw new Exception("The webpage or PDF is larger than the 5 MB download limit.");
                }

                byte[] bytes;
                try (InputStream input = connection.getInputStream()) {
                    bytes = readBounded(input);
                }
                if (pdf) return new Page(current, "PDF document", "", readPdfText(context, bytes));
                String source = new String(bytes, resolveCharset(type));
                String title = extractTitle(source);
                String description = extractDescription(source);
                String body = lowerType.startsWith("text/plain") ? source : stripHtml(source);
                body = cleanText(body);
                if (body.length() > MAX_TEXT_CHARS) body = body.substring(0, MAX_TEXT_CHARS);
                if (body.trim().isEmpty()) {
                    throw new Exception("The page did not expose readable text. Script-only sites and login pages may not be supported.");
                }
                return new Page(current, title, description, body);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new Exception("The webpage redirect limit was exceeded.");
    }

    private static String normalize(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty() || raw.length() > 2048) {
            throw new Exception("Please provide one valid webpage link (maximum 2,048 characters).");
        }
        String value = raw.trim();
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception error) {
            throw new Exception("That link is not a valid URL.");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
            throw new Exception("Only HTTPS webpage links are supported.");
        }
        return uri.toASCIIString();
    }

    private static URI validatePublicHttpUrl(String value) throws Exception {
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception error) {
            throw new Exception("The webpage supplied an invalid redirect URL.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || !"https".equalsIgnoreCase(scheme)) {
            throw new Exception("Only public HTTPS webpages can be opened.");
        }
        if (uri.getRawUserInfo() != null || uri.getPort() != -1
                && !(("http".equalsIgnoreCase(scheme) && uri.getPort() == 80)
                || ("https".equalsIgnoreCase(scheme) && uri.getPort() == 443))) {
            throw new Exception("Links with embedded credentials or non-standard ports are not supported.");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost")
                || lowerHost.endsWith(".local") || lowerHost.endsWith(".internal")) {
            throw new Exception("Private or local-network addresses cannot be opened by the webpage reader.");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new Exception("The webpage host could not be resolved.");
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new Exception("Private, local, or reserved network addresses are blocked.");
            }
        }
        return uri;
    }

    private static String readPdfText(Context context, byte[] bytes) throws Exception {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        try (InputStream input = new ByteArrayInputStream(bytes);
             PDDocument document = PDDocument.load(input)) {
            int pages = document.getNumberOfPages();
            if (pages <= 0) throw new Exception("The PDF contains no readable pages.");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(pages, 20));
            String text = cleanText(stripper.getText(document));
            if (text.isEmpty()) throw new Exception("No selectable text was found. Scanned-PDF OCR is not enabled.");
            boolean truncated = pages > 20 || text.length() > MAX_TEXT_CHARS;
            if (text.length() > MAX_TEXT_CHARS) text = text.substring(0, MAX_TEXT_CHARS);
            if (truncated) text += "\n\n[PDF preview limited to 20 pages and 16,000 characters.]";
            return text;
        }
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
            if (a == 100 && b >= 64 && b <= 127) return false;
            if (a == 169 && b == 254) return false;
            if (a == 172 && b >= 16 && b <= 31) return false;
            if (a == 192 && (b == 168 || b == 0 && c == 0 || b == 0 && c == 2)) return false;
            if (a == 198 && (b == 18 || b == 19 || b == 51 && c == 100)) return false;
            if (a == 203 && b == 0 && c == 113) return false;
            return true;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = bytes[0] & 255;
            int second = bytes[1] & 255;
            if (first == 0xff || (first & 0xfe) == 0xfc) return false;
            if (first == 0xfe && (second & 0xc0) == 0x80) return false;
            return true;
        }
        return false;
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                throw new Exception("The webpage or PDF exceeded the 5 MB download limit.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static Charset resolveCharset(String contentType) {
        if (contentType != null) {
            Matcher matcher = Pattern.compile("(?i)charset\\s*=\\s*[\\\"]?([^;\\\" ]+)")
                    .matcher(contentType);
            if (matcher.find()) {
                try { return Charset.forName(matcher.group(1).trim()); }
                catch (Exception ignored) { }
            }
        }
        return Charset.forName("UTF-8");
    }

    private static String extractTitle(String html) {
        Matcher matcher = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title>").matcher(html);
        return matcher.find() ? cleanText(decodeEntities(matcher.group(1))).trim() : "";
    }

    private static String extractDescription(String html) {
        Pattern pattern = Pattern.compile(
                "(?is)<meta\\b(?=[^>]*(?:name|property)\\s*=\\s*['\\\"](?:description|og:description)['\\\"])[^>]*content\\s*=\\s*['\\\"](.*?)['\\\"][^>]*>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) return cleanText(decodeEntities(matcher.group(1))).trim();
        return "";
    }

    private static String stripHtml(String html) {
        String value = html.replaceAll("(?is)<!--.*?-->", " ");
        String[] blockedTags = new String[]{"script", "style", "noscript", "svg", "iframe", "form"};
        for (String tag : blockedTags) {
            value = value.replaceAll("(?is)<" + tag + "\\b[^>]*>.*?</" + tag + "\\s*>", " ");
        }
        value = value.replaceAll("(?is)<(?:br|hr)\\b[^>]*>", "\n");
        value = value.replaceAll("(?is)</(?:p|div|article|section|h[1-6]|li|tr|blockquote)\\s*>", "\n");
        value = value.replaceAll("(?s)<[^>]+>", " ");
        return decodeEntities(value);
    }

    private static String decodeEntities(String value) {
        String decoded = value.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
        Matcher matcher = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(decoded);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            try {
                String number = matcher.group(1);
                int codePoint = number.startsWith("x") || number.startsWith("X")
                        ? Integer.parseInt(number.substring(1), 16) : Integer.parseInt(number);
                String replacement = Character.isValidCodePoint(codePoint)
                        ? new String(Character.toChars(codePoint)) : "";
                matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
            } catch (Exception ignored) {
                matcher.appendReplacement(output, "");
            }
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String cleanText(String value) {
        return value.replace('\u00a0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
