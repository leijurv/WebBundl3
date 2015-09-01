/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jsbundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 *
 * @author leijurv
 */
public class JSBundle {

    public static byte[] getHTML(File f) {
        if (verbose) {
            System.out.print("Loading into memory " + f.getAbsolutePath() + "... ");
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] x = new byte[Math.max(in.available(), 65536)];
            int i;
            while ((i = in.read(x)) >= 0) {
                out.write(x, 0, i);
            }
            byte[] resp = out.toByteArray();
            if (verbose) {
                System.out.println("done");
            }
            return resp;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        } catch (IOException ex) {
            Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        throw new IllegalStateException("unable to load " + f);
    }
    static boolean mobile;
    static boolean desktop;
    static boolean common;
    static File base;
    static boolean js;
    static boolean verbose;
    static String searchString;
    static String ext;
    static Random r = new Random();

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (Arrays.asList(args).contains("-v")) {
            verbose = true;
            System.out.println("Running WebBundl3 in verbose mode");
        }
        if (Arrays.asList(args).contains("--css") || Arrays.asList(args).contains("-c")) {
            ext = "css";
        } else if (Arrays.asList(args).contains("--js") || Arrays.asList(args).contains("-j")) {
            js = true;
            ext = "js";
        } else {
            System.out.println("Error! Missing '--css' or '--js'");
            return;
        }
        if (verbose) {
            System.out.println("Running " + ext + " bundle on " + args[0] + "... ");
        }
        searchString = "<" + (js ? "script src" : "link href") + "=\"";
        File file = new File(args[0]).getAbsoluteFile();
        common = file.getAbsolutePath().contains("common");
        desktop = file.getAbsolutePath().contains("desktop");
        mobile = file.getAbsolutePath().contains("mobile");
        if (verbose) {
            System.out.println("Common: " + common + " Desktop: " + desktop + " Mobile: " + mobile);
        }
        if (common) {
            base = file.getAbsoluteFile().getParentFile().getParentFile();
        } else {
            base = file.getAbsoluteFile().getParentFile().getParentFile().getParentFile();
        }
        String html = new String(getHTML(file));
        ArrayList<Object> parsed = new ArrayList<>();
        parsed.add(html);
        parse(parsed);
        if (parsed.size() == 1) {
            if (verbose) {
                System.out.println("no " + ext + " tags, done");
            }
            return;
        }
        System.out.print("Bundling " + ext + " in " + args[0] + "... ");
        merge(parsed);
        try (FileOutputStream rewrite = new FileOutputStream(file)) {
            parsed.parallelStream().map(o -> {
                if (o instanceof Merged) {
                    ((Merged) o).save();
                }
                return o;
            }).map((o) -> {
                if (o instanceof Merged) {
                    return (((Merged) o).toHTML().getBytes());
                } else {
                    if (o instanceof ScriptTag) {
                        return (((ScriptTag) o).toHTML().getBytes());
                    } else {
                        return ((String) o).getBytes();
                    }
                }
            }).forEachOrdered(o -> {
                try {
                    rewrite.write(o);
                } catch (IOException ex) {
                    Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        } catch (FileNotFoundException ex) {
            Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("done");
    }

    public static void merge(ArrayList<Object> parsed) {
        for (int i = 0; i < parsed.size(); i++) {
            if (parsed.get(i) instanceof ScriptTag) {
                int j;
                for (j = i + 1; j < parsed.size(); j++) {
                    if (!(parsed.get(j) instanceof ScriptTag)) {
                        break;
                    }
                }
                j--;
                if (verbose) {
                    System.out.println("Script tags from " + i + " to " + j);
                }
                if (i == j) {
                    continue;//ignore script tags on their own
                }
                if (verbose) {
                    System.out.println("Merging");
                }
                ScriptTag[] scriptTags = new ScriptTag[j - i + 1];
                for (int k = i; k <= j; k++) {
                    scriptTags[k - i] = (ScriptTag) parsed.remove(i);
                }
                parsed.add(i, new Merged(scriptTags));
            }
        }
    }

    public static void parse(ArrayList<Object> parsed) {
        for (int i = 0; i < parsed.size(); i++) {
            if (parsed.get(i) instanceof String) {
                String html = (String) parsed.get(i);

                int index = html.indexOf(searchString);
                if (index == -1) {
                    continue;
                }
                int o = searchString.length();
                int end = find(html, index + o, '"');//The next quote mark will be the endquote matching the start quote in <blah src="
                if (end < 0) {
                    break;
                }
                String jsSource = html.substring(index + o, end);
                ScriptTag scriptTag = new ScriptTag(jsSource);
                int tagEnd = (js ? html.indexOf("t>", end + 3) + 2 : html.indexOf(">", end + 3) + 1);
                String fullTag = html.substring(index, tagEnd);
                String x = scriptTag.toHTML();
                if (!x.equals(fullTag)) {
                    if (verbose) {
                        System.out.println("Hoping that " + fullTag + " and " + scriptTag.toHTML() + " are the same...");
                    }
                }
                //System.out.println(fullTag);
                //System.out.println(jsSource);
                String beforeThisTag = html.substring(0, index);
                String afterThisTag = html.substring(tagEnd, html.length());
                parsed.remove(i);
                parsed.add(i, beforeThisTag);
                parsed.add(i + 1, scriptTag);
                parsed.add(i + 2, afterThisTag);
                i--;
            }
        }
        for (int i = 0; i < parsed.size(); i++) {
            Object o = parsed.get(i);
            if (o instanceof String) {
                if (isEmpty((String) o)) {
                    parsed.remove(i);
                    i--;
                }
            }
        }
    }

    public static boolean isEmpty(String s) {
        for (char c : s.toCharArray()) {
            switch (c) {
                case ' ':
                case '\n':
                case '\r':
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    public static class Merged {

        final ScriptTag[] tags;
        final long id;
        final String name;
        boolean isjquery;

        public Merged(ScriptTag[] tags) {
            this.tags = tags;
            isjquery = common && Stream.of(tags).anyMatch(t -> t.src.contains("jquery"));//true if any of the tags contain the string "jquery"
            if (isjquery) {
                System.out.println("This is jQuery");
            }
            Arrays.asList(tags).parallelStream().map((t)
                    -> t.getContents()
            ).distinct().count();
            id = Math.abs(r.nextLong());
            name = (isjquery ? "j" : "") + "bundle_" + id + "." + ext;
        }

        @Override
        public String toString() {
            return "Merge (id " + id + ") of " + Arrays.asList(tags);
        }

        public void save() {
            File output = getOutputFile(name);
            if (verbose) {
                System.out.println("Writing " + this + " to " + output);
            }
            try (FileOutputStream out = new FileOutputStream(output)) {
                if (!common && js) {
                    out.write("(function(){".getBytes());
                    if (verbose) {
                        System.out.println("Wrapping in (function(){");
                    }
                } else {
                    if (verbose) {
                        System.out.println("NOT wrapping in (function(){");
                    }
                }
                for (ScriptTag t : tags) {
                    out.write(t.getContents());
                    out.write("\n\n".getBytes());
                }
                if (!common && js) {
                    out.write("})();".getBytes());
                }
            } catch (FileNotFoundException ex) {
                Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        public String toHTML() {
            return new ScriptTag(name).toHTML();
        }
    }

    public static File getOutputFile(String name) {
        if (common) {
            return new File(base.getAbsolutePath() + "/common/" + name);
        }
        if (desktop) {
            new File(base.getAbsolutePath() + "/desktop/" + ext + "/").mkdirs();
            return new File(base.getAbsolutePath() + "/desktop/" + ext + "/" + name);
        }
        if (mobile) {
            new File(base.getAbsolutePath() + "/mobile/" + ext + "/").mkdirs();
            return new File(base.getAbsolutePath() + "/mobile/" + ext + "/" + name);
        }
        throw new IllegalStateException("your mom");
    }

    public static class ScriptTag {

        final String src;
        byte[] contents = null;

        public ScriptTag(String src) {
            this.src = src;
        }

        public byte[] getContents() {
            if (contents == null) {
                contents = fetchContents();
            }
            return contents;
        }

        private byte[] fetchContents() {
            if (verbose) {
                System.out.println("FETCHING " + src);
            }
            if (src.startsWith("http")) {
                try {
                    return getText(src);
                } catch (Exception ex) {
                    Logger.getLogger(JSBundle.class.getName()).log(Level.SEVERE, null, ex);
                    System.exit(1);
                }
                return null;
            }
            if (common) {
                return getHTML(new File(base.getAbsolutePath() + "/common/" + src));
            }
            if (desktop) {
                File desk = new File(base.getAbsolutePath() + "/desktop/" + ext + "/" + src);
                File comm = new File(base.getAbsolutePath() + "/common/" + src);
                if (comm.exists()) {
                    return getHTML(comm);
                }
                return getHTML(desk);
            }
            if (mobile) {
                File mobi = new File(base.getAbsolutePath() + "/mobile/" + ext + "/" + src);
                File comm = new File(base.getAbsolutePath() + "/common/" + src);
                if (comm.exists()) {
                    return getHTML(comm);
                }
                return getHTML(mobi);
            }
            throw new IllegalStateException("Failed to locate source for " + src);
        }

        @Override
        public String toString() {
            return "SCRIPT TAG " + src;
        }

        public String toHTML() {
            if (js) {
                return "<script src=\"" + src + "\"></script>";
            } else {
                return "<link href=\"" + src + "\" rel=\"stylesheet\" />";
            }
        }
    }

    public static int find(String x, int ind, char f) {
        int l = x.length();
        while (ind < l) {
            if (x.charAt(ind) == f) {
                return ind;
            }
            ind++;
        }
        return -1;
    }

    public static byte[] getText(String url) throws Exception {
        if (verbose) {
            System.out.println("Loading into memory " + url);
        }
        URL website = new URL(url);
        URLConnection connection = website.openConnection();
        InputStream in = connection.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] x = new byte[Math.max(in.available(), 65536)];
        int i;
        while ((i = in.read(x)) >= 0) {
            out.write(x, 0, i);
        }
        byte[] resp = out.toByteArray();
        if (verbose) {
            System.out.println("Done loading into memory " + url);
        }
        return resp;
    }
}
