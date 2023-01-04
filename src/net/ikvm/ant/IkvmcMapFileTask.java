package net.ikvm.ant;

import java.io.PrintWriter;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.ExecutableMemberDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;

/**
 * Java doclet that generates mapping file for IKVMC.
 *
 * Among other things mapping file can specify parameter names in methods and constructors translated by IKVMC.
 * Since Java parameter names are not available from Java class files, IKVMC needs this hint to preserve original Java names.
 *
 */
public class IkvmcMapFileTask implements DocletTask {

    private final PrintWriter out;
    private final String assemblyName;


    IkvmcMapFileTask (String assemblyName, PrintWriter out) {
        this.out = out;
        this.assemblyName = assemblyName;
    }

    @Override
    public void process(RootDoc root) {
        out.print  ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.printf ("<!-- %s mapping file for IKVMC Auto-generated by IkvmcDoclet -->\n", assemblyName);
        out.print  ("<root>\n");
        out.print  (" <assembly>\n");
        for (ClassDoc cd : root.classes ())
            printClassDoc (cd);

        out.print  (" </assembly>\n");
        out.print  ("</root>\n");
        out.close ();
    }


    private void         printClassDoc (ClassDoc cd) {
        String className = cd.qualifiedTypeName ();

        out.printf  ("  <class name=\"%s\" modifiers=\"%s\">\n", className, cd.modifiers());

        //TODO: <implements class="..." />

        for (ConstructorDoc c : cd.constructors (true))
            printConstructor (c);


        for (MethodDoc m : cd.methods(true) )
            printMethod (m);

//      for (FieldDoc f : cd.fields(true) )
//      printField (f);

        out.print   ("  </class>\n");
    }

//  private static void printField(FieldDoc f) {
//      if (isEmptyDoc(f))
//          return;
//
//      mOut.printf  ("  <member name=\"F:%s.%s\">\n", f.containingClass().qualifiedTypeName(), f.name());
//        mOut.print   ("   <summary>");
//
//        for (Tag t : f.inlineTags ())
//            printTag (t, false);
//
//        mOut.print   ("</summary>\n");
//        mOut.print   ("  </member>\n");
//  }

    private void printConstructor(ConstructorDoc c) {
        out.printf  ("  <constructor sig=\"%s\" >\n", getJavaSignature (c));
        printExecutableMember(c);
        out.print   ("  </constructor>\n");
    }

    private void printMethod(MethodDoc m) {
        out.printf  ("  <method name=\"%s\" sig=\"%s\" modifiers=\"%s\">\n", m.name(), getJavaSignature (m), eatSynchronized(m.modifiers()));
        printExecutableMember(m);
        out.print   ("  </method>\n");
    }

    private void printExecutableMember(ExecutableMemberDoc m) {
        for (ClassDoc e : m.thrownExceptions()) {
            out.printf  ("     <throws class=\"%s\"/>\n", e.qualifiedTypeName());
        }

        // we need to print names for all parameters in exactly the same order as in method signature (for IKVMC to work)
        for (Parameter p : m.parameters()) {
            out.printf  ("     <parameter name=\"%s\"/>\n", p.name());
        }
    }


    private String getJavaSignature(ExecutableMemberDoc method) {
        StringBuilder result = new StringBuilder();

        result.append('(');
        for (Parameter parameter : method.parameters())
            appendJavaTypeName(result, parameter.type());
        result.append(')');
        if (method instanceof MethodDoc)
            appendJavaTypeName(result, ((MethodDoc) method).returnType());
        else
            result.append('V'); // New since IKVM 0.38
        return result.toString();
    }


    private void appendJavaTypeName (StringBuilder sb, Type type) {

        if (type == null) {
            sb.append("V");
            return;
        }

        String dim = type.dimension(); // something like "[][]"
        if (dim != null && dim.length() > 0) {
            int d = dim.length() /2;
            while (d -- > 0)
                sb.append ('[');

        }
        final String name = type.qualifiedTypeName();  // For example, a two dimensional array of String returns "java.lang.String".
        if (name.equals("int"))
            sb.append("I");
        else if (name.equals("byte"))
            sb.append("B");
        else if (name.equals("char"))
            sb.append("C");
        else if (name.equals("long"))
            sb.append("J");
        else if (name.equals("short"))
            sb.append("S");
        else if (name.equals("float"))
            sb.append("F");
        else if (name.equals("double"))
            sb.append("D");
        else if (name.equals("boolean"))
            sb.append("Z");
        else if (name.equals("void"))
            sb.append("V");
        else {

            TypeVariable tv = type.asTypeVariable();
            if (tv != null) {
                Type [] bounds = tv.bounds();
                if (bounds != null && bounds.length == 1) {
                    appendJavaTypeName (sb, bounds[0]);
                } else {
                    sb.append("Ljava.lang.Object;");
                }

            } else {
                sb.append('L');
                String unqualifiedName = type.typeName(); // For example, a two dimensional array of String returns "String".
                String innerName = type.simpleTypeName(); // For example, the class Outer.Inner returns "Inner".
                if ( ! innerName.equals(unqualifiedName)) {
                    // nested class
                    sb.append(name.replaceFirst("\\." + innerName + "$", "\\$" + innerName));
                } else {
                    sb.append(name);
                }

                sb.append(';');

            }


        }
    }

    private static String eatSynchronized (String s) {
        return s.replaceFirst("synchronized", "");
    }
}