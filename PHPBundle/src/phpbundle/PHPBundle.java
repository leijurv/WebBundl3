/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package phpbundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author leijurv
 */
public class PHPBundle {

    static boolean verbose = true;
    static File base;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        File f = new File(args[0]).getAbsoluteFile();
        base = f.getParentFile();
        String contents = new String(getHTML(f));
        //System.out.println(contents);
        ArrayList<Object> parsed = parse(contents);
        //System.out.println(parsed);
        resolveImports(parsed);
        //System.out.println(parsed);
        String result = parsed.parallelStream().filter(x -> !isEmpty(x instanceof PHPTag ? ((PHPTag) x).contents : (String) x)).map(x -> (x instanceof PHPTag ? ((PHPTag) x).toHTML() : (String) x)).collect(Collectors.joining());
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(result.getBytes());
        } catch (FileNotFoundException ex) {
            Logger.getLogger(PHPBundle.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PHPBundle.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void resolveImports(ArrayList<Object> parsed) {
        for (int i = 0; i < parsed.size(); i++) {
            Object o = parsed.get(i);
            if (o instanceof PHPTag) {
                PHPTag phptag = (PHPTag) o;
                String contents = phptag.contents;
                String before = null;
                String ref = null;
                String after = null;
                int ind = -1;
                while ((ind = contents.indexOf("include(\"", ind + 1)) != -1) {
                    int endQuote = contents.indexOf("\");", ind);
                    before = contents.substring(0, ind);
                    ref = contents.substring(ind + 9, endQuote);
                    after = contents.substring(endQuote + 3, contents.length());
                    if (!ref.contains("/")) {
                        break;
                    }
                }
                if (ind == -1) {
                    continue;
                }
                parsed.remove(i);
                System.out.println(ref);
                parsed.add(i, new PHPTag(before));
                String path = base.toString() + "/" + ref;
                System.out.println(path);
                String refContents = new String(getHTML(new File(path)));
                parsed.add(i + 1, refContents);
                parsed.add(i + 2, new PHPTag(after));
            }
        }
    }

    public static ArrayList<Object> parse(String aoeuaoeuaoeueoau) {
        String php = aoeuaoeuaoeueoau;
        ArrayList<Object> cats = new ArrayList<Object>();
        int ind;
        while ((ind = php.indexOf("<?php")) != -1) {
            String before = php.substring(0, ind);
            int end = php.indexOf("?>");
            String during = php.substring(ind + 5, end);
            String after = php.substring(end + 2, php.length());
            //System.out.println("Before: " + before);
            //System.out.println("During: " + during);
            //System.out.println("After: " + after);
            cats.add(before);
            cats.add(new PHPTag(during));
            php = after;
        }
        cats.add(php);
        return cats;
    }

    public static class PHPTag {

        String contents;

        public PHPTag(String contents) {
            this.contents = contents;
        }

        @Override
        public String toString() {
            return "PHPTAG: " + contents;
        }

        public String toHTML() {
            return "<?php" + contents + "?>";
        }
    }

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
            Logger.getLogger(PHPBundle.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        } catch (IOException ex) {
            Logger.getLogger(PHPBundle.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(PHPBundle.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        throw new IllegalStateException("unable to load " + f);
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
}
