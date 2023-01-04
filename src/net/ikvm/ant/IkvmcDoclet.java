package net.ikvm.ant;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;


/**
 * Java doclet that generates mapping file for IKVMC.
 *
 * Among other things mapping file can specify parameter names in methods and constructors translated by IKVMC.
 * Since Java parameter names are not available from Java class files, IKVMC needs this hint to preserve original Java names.
 *
 *
 * Also generates Microsoft XML Help file based on javadoc.
 */
public class IkvmcDoclet {


    private static final String ASSEMBLY_NAME_ARG = "-ant-ikvm:assembly";
    private static final String MAP_OUTPUT_ARG = "-ant-ikvm:mapfile";
    private static final String HELP_OUTPUT_ARG = "-ant-ikvm:helpfile";


    public static LanguageVersion languageVersion () {
        return (LanguageVersion.JAVA_1_5);
    }

    // Doclet API methods

    public static boolean validOptions(String options[][], DocErrorReporter reporter) {
        return true; //TODO: Validate
    }

    public static int optionLength(String option) {
        if(option.equals(ASSEMBLY_NAME_ARG) || option.equals(MAP_OUTPUT_ARG) || option.equals(HELP_OUTPUT_ARG)) {
            return 2;
        }
        return 0;
    }

    public static boolean       start (RootDoc root) {
        Collection <DocletTask> tasks = readOptions (root.options());

        for (DocletTask task : tasks)
            task.process(root);

        return true;
    }

    private static Collection<DocletTask> readOptions(String[][] options) {
        String assemblyName = "default";
        PrintWriter mapOutput = null;
        PrintWriter helpOutput = null;
        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            if (opt[0].equals(ASSEMBLY_NAME_ARG)) {
                assemblyName = opt[1];
            }
            if (opt[0].equals(MAP_OUTPUT_ARG)) {
                mapOutput = getPrintWriter (opt[1]);
            } else
            if (opt[0].equals(HELP_OUTPUT_ARG)) {
                helpOutput = getPrintWriter (opt[1]);
            }
        }

        List<DocletTask> result = new ArrayList<DocletTask>(3);
        if (mapOutput != null)
            result.add(new IkvmcMapFileTask (assemblyName, mapOutput));
        if (helpOutput != null)
            result.add(new IkvmcHelpFileTask (assemblyName, helpOutput));

        return result;
    }

    private static PrintWriter getPrintWriter (String fileName) {
        try {
            return new PrintWriter (fileName, "UTF8");
        } catch (UnsupportedEncodingException unexpected) {
        	throw new RuntimeException (unexpected);
        } catch (FileNotFoundException e) {
            throw new RuntimeException ("Can't open output file \"" + fileName + "\". Cause: "+ e.getMessage(), e);
        }
    }




}
