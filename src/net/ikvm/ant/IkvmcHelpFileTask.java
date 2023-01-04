package net.ikvm.ant;

import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.ExecutableMemberDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import com.sun.javadoc.ThrowsTag;
import com.sun.javadoc.Type;

/**
*
* Generates XML documentation for source files in Microsoft help format (based on javadoc).
*
* See also <a href='http://msdn.microsoft.com/en-us/library/aa288481(VS.71).aspx'>XML documentation format</a>.
*/
public class IkvmcHelpFileTask implements DocletTask {

    private final PrintWriter out;
    private final String assemblyName;
    private final List<ParamTag> paramTagsList = new ArrayList<ParamTag>();
    private final Collection<ClassDoc> printedExceptions = new ArrayList<ClassDoc>();
    private final XmlValidator validator = new XmlValidator ();

    IkvmcHelpFileTask (String assemblyName, PrintWriter out) {
        this.out = out;
        this.assemblyName = assemblyName;
    }

    @Override
    public void process(RootDoc root) {

        out.print  ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.printf ("<!-- Help file for %s assembly auto-generated by IkvmcDoclet -->\n", assemblyName);
        out.print  ("<doc>\n");
        out.print  (" <assembly>\n");
        out.printf ("  <name>%s</name>\n", assemblyName);
        out.print  (" </assembly>\n");
        out.print  (" <members>\n");

        printPackageDocs (root);

        for (ClassDoc cd : root.classes ())
            printClassDoc (cd);

        out.print  (" </members>\n");
        out.print  ("</doc>\n");
        out.close ();

    }


    private void printPackageDocs(RootDoc root) {
        Set<PackageDoc> packages = new HashSet<PackageDoc>();
        for (ClassDoc cd : root.classes ()) {
            packages.add(cd.containingPackage());
        }

        for (PackageDoc pd : packages)
            printPackageDoc (pd);

    }

    private void printPackageDoc(PackageDoc pd) {
        if (isEmptyDoc(pd))
            return;

        validator.validate (pd);

        out.printf  ("  <member name=\"N:%s\">\n", pd.name());
        out.print   ("   <summary>");
        for (Tag t : pd.inlineTags ())
            printTag (t);
        out.print   ("</summary>\n");
        out.print   ("  </member>\n");
    }

//    private void         print (char ch) {
//        switch (ch) {
//            case '"':   out.print ("&quot;");      break;
//            case '\'':  out.print ("&apos;");      break;
//            case '&':   out.print ("&amp;");       break;
//            case '<':   out.print ("&lt;");        break;
//            case '>':   out.print ("&gt;");        break;
//            default:    out.print (ch);            break;
//        }
//    }

//    private void         print (String text) {
//        int             len = text.length ();
//
//        for (int ii = 0; ii < len; ii++)
//            print (text.charAt (ii));
//    }

    private void         printTag (Tag t) {
        String          kind = t.kind ();

        if (kind.equals ("Text") || kind.equals ("@return")) {
            out.print (convertTags(t.text()));
        } else if (t instanceof SeeTag) {
            SeeTag      see = (SeeTag) t;

            MemberDoc   member = see.referencedMember();

            if (member != null) {
                String seeTagChar;
                String memberName = see.referencedMemberName ();
                if (member instanceof MethodDoc) {
                    seeTagChar = "M";
                    memberName = memberName + getDotNetSignature((MethodDoc)member);
                } else if (member instanceof FieldDoc) {
                    seeTagChar = "F"; //P?
                } else if (member instanceof ConstructorDoc) {
                    seeTagChar = "M";
                    memberName = "#ctor" + getDotNetSignature((ConstructorDoc)member);
                } else
                    throw new IllegalStateException ("Unsupported MemberDoc type:" + member.getClass().getSimpleName());

                // in some cases reference points to a member declared in base class (that's why we can't use see.referencedClassName () here
                String      rc = see.referencedMember().containingClass().qualifiedTypeName();
                out.printf ("<see cref=\"%s:%s.%s\"/> ", seeTagChar, rc, memberName);

            } else {
                ClassDoc rc = see.referencedClass ();
                String rcname = see.referencedClassName ();
                if (rcname != null) {
                    if (rc != null)
                        out.printf ("<see cref=\"T:%s\"/> ", rcname);
                    else
                        out.printf ("<see cref=\"N:%s\"/> ", rcname);
                } else {
                    out.print (see.text()); // @see <a href="java.sun.com">.
                }
            }



        } else if (t instanceof ParamTag) {
            ParamTag p = (ParamTag)t;
            out.printf  ("   <param name=\"%s\">", p.parameterName());

            Tag [] inlineTags = p.inlineTags();
            if (inlineTags.length > 0) {
                for (Tag inlineTag : inlineTags)
                    printTag (inlineTag);
            } else {
                String comment = p.parameterComment();
                if (comment != null && comment.trim().length() > 0)
                    out.print (eatNewLines(comment));
            }

            out.println("</param>");
        } else if (t instanceof ThrowsTag) {
            ThrowsTag tt = (ThrowsTag)t;
            Type et = tt.exceptionType();
            out.printf  ("   <exception cref=\"T:%s\">%s</exception>\n", et.qualifiedTypeName(), eatNewLines(tt.exceptionComment()));
        } else
            System.err.println ("UNHANDLED: Tag kind: " + kind);
    }


    private final StringBuffer textConversionBuffer1 = new StringBuffer ();
    private final StringBuffer textConversionBuffer2 = new StringBuffer ();

    private String convertTags(String text) {
        convertCode2TT (text, textConversionBuffer1);
        convertPre2Code (textConversionBuffer1, textConversionBuffer2);
        return textConversionBuffer2.toString();
    }

    private void         printClassDoc (ClassDoc cd) {
        out.printf  ("  <member name=\"T:%s\">\n", cd.qualifiedTypeName ());
        out.printf  ("   <summary>");

        validator.validate (cd);

        for (Tag t : cd.inlineTags ())
            printTag (t);

        out.print   ("</summary>\n");
        out.print   ("  </member>\n");

        for (ConstructorDoc c : cd.constructors (true))
            printConstructor (c);

        for (FieldDoc f : cd.fields(true) )
            printField (f);

        for (MethodDoc m : cd.methods(true) )
            printMethod (m);

    }

    private void printField(FieldDoc f) {

        if (isEmptyDoc(f))
            return;

        validator.validate (f);

        out.printf  ("  <member name=\"F:%s.%s\">\n", f.containingClass().qualifiedTypeName(), f.name());
        out.print   ("   <summary>");

        for (Tag t : f.inlineTags ())
            printTag (t);

        out.print   ("</summary>\n");
        out.print   ("  </member>\n");
    }

    private void printConstructor(ConstructorDoc c) {
        printExecutableMember(c, "#ctor");
    }

    private void printMethod(MethodDoc m) {
        printExecutableMember(m, m.name());
    }

    private void printExecutableMember(ExecutableMemberDoc m, String dotNetMemberName) {
        ClassDoc cls = m.containingClass();

        final ClassDoc [] exceptions = m.thrownExceptions();
        final ThrowsTag [] throwsTags = m.throwsTags();
        final ParamTag [] paramsTags = m.paramTags();
        if (isEmptyDoc(m) && paramsTags.length == 0 && exceptions.length == 0 && throwsTags.length == 0)
            return;

        validator.validate (m);

        String dotNetSignature = getDotNetSignature(m);
        String javaSignature = getJavaSignature (m);
        String javaMemberName = m.name();
        String className = cls.qualifiedTypeName();


        out.printf  ("  <member name=\"M:%s.%s%s\" id=\"%s.%s%s\">\n", className, dotNetMemberName, dotNetSignature, className, javaMemberName, javaSignature);
        out.print   ("   <summary>");

        for (Tag t : m.inlineTags ())
            printTag (t);

        out.print   ("</summary>\n");

        // we need to print names for all parameters in exactly the same order as in method signature (for IKVMC to work)
        paramTagsList.clear();
        for (ParamTag p : paramsTags)
            paramTagsList.add(p);

        for (Parameter para : m.parameters()) {
            String parameterName = para.name();
            ParamTag p = null;
            final int cnt = paramTagsList.size();
            for (int i = 0; i < cnt; i++) {
                ParamTag pt = paramTagsList.get(i);
                if (pt.parameterName().equals(parameterName)) {
                    p = pt;
                    paramTagsList.remove (i);
                    break;
                }
            }

            if (p != null) {
                printTag (p);
            } else {
                // just print formal parameter name so that IKVM can use it in code generation
                out.printf  ("   <param name=\"%s\"/>\n", parameterName);
            }
        }


        Tag [] returnTags = m.tags("@return");
        if (returnTags != null && returnTags.length > 0) {

            out.print  ("   <returns>");
            printTag (returnTags[0]);
            out.println  ("</returns>");

        }

        printedExceptions.clear();
        for (ThrowsTag t : throwsTags) {
            printedExceptions.add(t.exception());
            printTag (t);
        }

        for (ClassDoc e : exceptions) {
            if ( ! printedExceptions.contains(e))
                out.printf  ("   <exception cref=\"T:%s\">Undocumented throwable exception.</exception>\n", e.qualifiedTypeName());
        }

        out.print   ("  </member>\n");
    }

    private static boolean isEmptyDoc(Doc d) {
        String comment = d.getRawCommentText();
        return comment == null || comment.trim().length() == 0;
    }

    private static String eatNewLines (String str) {
        StringBuilder sb = new StringBuilder (256);
        boolean eatSpace = true;
        for (char ch : str.toCharArray()) {
            final boolean isSpace = Character.isWhitespace(ch);
            if ( ! isSpace || ! eatSpace) {
                if (isSpace && (ch == '\n' || ch == '\r'))
                    ch = ' ';
                sb.append(ch);
            }
            eatSpace = isSpace;
        }
        return sb.toString();
    }

//    private static final Field signatureMethod;
//    static {
//        try {
//            signatureMethod = Method.class.getDeclaredField("signature");
//            signatureMethod.setAccessible(true);
//        } catch (Exception e) {
//            throw new ExceptionInInitializerError (e);
//        }
//    }


    private static String getDotNetSignature(ExecutableMemberDoc method) {
        StringBuilder result = new StringBuilder();

        Parameter [] parameters = method.parameters();
        if (parameters.length > 0) {
            result.append('(');
            boolean needComma = false;
            for (Parameter parameter : parameters) {
                if (needComma)
                    result.append(',');
                else
                    needComma = true;
                appendDotNetTypeName(result, parameter.type());
            }
            result.append(')');
        }
        //NOTE: No return value type in .NET signature

        return result.toString();
    }

    private static String getJavaSignature(ExecutableMemberDoc method) {
        StringBuilder result = new StringBuilder();

        result.append('(');
        for (Parameter parameter : method.parameters())
            appendJavaTypeName(result, parameter.type());
        result.append(')');
        if (method instanceof MethodDoc)
            appendJavaTypeName(result, ((MethodDoc) method).returnType());

        return result.toString();
    }

/*   REFLECTION-BASED Approach:

        Method method = getClassMethod (m); Handle constructors separately?
        String dotNetSignature = getDotNetSignature(method);
        String javaSignature = getJavaSignature (method);

    private static Method getClassMethod (ExecutableMemberDoc m) {
        try {
            Class <?> cls = Class.forName(m.containingClass().qualifiedName());
            Parameter[] parameters = m.parameters();
            Class <?> [] parameterTypes = new Class[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                parameterTypes[i] = classForName(parameters[i].type());
            }
            return cls.getDeclaredMethod(m.name(), parameterTypes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static String getDotNetSignature(Method method) {
        StringBuilder result = new StringBuilder();

        Class [] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 0) {
            result.append('(');
            boolean needComma = false;
            for (Class parameterType : parameterTypes) {
                if (needComma)
                    result.append(',');
                else
                    needComma = true;
                appendDotNetTypeName(result, parameterType);
            }
            result.append(')');
        }
        //NOTE: No return value type in .NET signature

        return result.toString();
    }

    private static String getJavaSignature(Method method) {
        StringBuilder result = new StringBuilder();

        //String signature = (String)signatureMethod.get(method);
        //if (signature != null) {
        //    escape (signature, result);

        result.append('(');
        for (Class parameterType : method.getParameterTypes())
            appendJavaTypeName(result, parameterType);
        result.append(')');
        appendJavaTypeName(result, method.getReturnType());

        return result.toString();
    }
    private static void appendJavaTypeName (StringBuilder sb, Class cls) {

        if (cls.isPrimitive()) {
            if (cls == int.class)
                sb.append("I");
            else if (cls == float.class)
                sb.append("F");
            else if (cls == double.class)
                sb.append("D");
            else if (cls == byte.class)
                sb.append("B");
            else if (cls == char.class)
                sb.append("C");
            else if (cls == long.class)
                sb.append("J");
            else if (cls == short.class)
                sb.append("S");
            else if (cls == boolean.class)
                sb.append("Z");
            else if (cls == void.class)
                sb.append("V");
            else
                throw new IllegalStateException (cls.getName());
        } else {
            final String name = cls.getName();

            if (cls.isArray()) {
                while (cls.isArray()) {
                    sb.append ('[');
                    cls = cls.getComponentType();
                }
                appendJavaTypeName(sb, cls);
            } else {
                sb.append('L');
                sb.append(name);
                sb.append(';');
            }


//            if (type instanceof ParameterizedType) {
//                java.lang.reflect.Type [] typeArgs = ((ParameterizedType)type).getActualTypeArguments();
//                if (typeArgs != null) {
//                    boolean needComma = false;
//                    if (typeArgs.length > 0) {
//                        sb.append (escape ? "&lt;" : "<" );
//                        for (java.lang.reflect.Type typeArg : typeArgs) {
//                            if (needComma)
//                                sb.append (',');
//                            else
//                                needComma = true;
//
//                            appendTypeName (sb, typeArg, escape);
//                        }
//                        sb.append (escape ? "&gt;" : ">" );
//                    }
//                }
//            }
        }
    }


    private static void appendDotNetTypeName (StringBuilder sb, Class cls) {


        if (cls.isPrimitive()) {
            if (cls == int.class)
                sb.append("System.Int32");
            else if (cls == float.class)
                sb.append("System.Float");
            else if (cls == double.class)
                sb.append("System.Double");
            else if (cls == byte.class)
                sb.append("System.Byte");
            else if (cls == char.class)
                sb.append("System.Char");
            else if (cls == long.class)
                sb.append("System.Int64");
            else if (cls == short.class)
                sb.append("System.Int16");
            else if (cls == boolean.class)
                sb.append("System.Boolean");
            else
                throw new IllegalStateException (cls.getName());
        } else {
            final String name = cls.getName();
            if (name.startsWith("java.lang.")) {
                sb.append("System");
                sb.append(name.substring(9));
            } else {
                if (cls.isArray()) {
                    int dim = 0;
                    while (cls.isArray()) {
                        dim++;
                        cls = cls.getComponentType();
                    }
                    appendDotNetTypeName(sb, cls);
                    while (dim-- > 0)
                        sb.append("[]");
                } else {
                    sb.append(name);
                }
            }
        }
    }

    private static Class classForName (com.sun.javadoc.Type type) throws Exception {

        String name = type.qualifiedTypeName();
        if (name.equals("int"))
            return int.class;

        if (name.equals("boolean"))
            return boolean.class;

        if (name.equals("long"))
            return long.class;

        if (name.equals("short"))
            return short.class;

        if (name.equals("byte"))
            return byte.class;

        if (name.equals("char"))
            return char.class;

        String dim = type.dimension();
        if (dim != null && dim.length() > 0) {
            int d = dim.length() / 2;
            name = 'L' + name + ';';
            while (d-- > 0)
                name = "[" + name;
        }

        return Class.forName(name);
    }

    private static void escape(String string, StringBuilder result) {
        final int cnt = string.length();
        for (int i=0; i<cnt; i++) {
            char ch = string.charAt(i);

            switch (ch) {
                case '"':   result.append ("&quot;");      break;
                case '\'':  result.append ("&apos;");      break;
                case '&':   result.append ("&amp;");       break;
                case '<':   result.append ("&lt;");        break;
                case '>':   result.append ("&gt;");        break;
                default:    result.append (ch);            break;
            }
        }
    }
*/

    private static void appendJavaTypeName (StringBuilder sb, Type type) {

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
            sb.append('L');
            sb.append(name);
            sb.append(';');

        }
    }


    private static void appendDotNetTypeName (StringBuilder sb, Type type) {

        final String name = type.qualifiedTypeName();  // For example, a two dimensional array of String returns "java.lang.String".
        if (name.equals("int"))
            sb.append("System.Int32");
        else if (name.equals("byte"))
            sb.append("System.Byte");
        else if (name.equals("char"))
            sb.append("System.Char");
        else if (name.equals("long"))
            sb.append("System.Int64");
        else if (name.equals("short"))
            sb.append("System.Int16");
        else if (name.equals("float"))
            sb.append("System.Float");
        else if (name.equals("double"))
            sb.append("System.Double");
        else if (name.equals("boolean"))
            sb.append("System.Boolean");
        else {
            if (name.startsWith("java.lang.")) {
                sb.append("System");
                sb.append(name.substring(9));
            } else {
                sb.append(name);
            }
        }

        String dim = type.dimension(); // something like "[][]"
        sb.append(dim);

    }

    private static class XmlValidator implements ErrorHandler {

        private final DocumentBuilder parser;
        private final StringBuilder rootedFragment = new StringBuilder ();

        XmlValidator () {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                parser = factory.newDocumentBuilder();
                parser.setErrorHandler(this);
            } catch (ParserConfigurationException e) {
                throw new RuntimeException (e);
            }
        }

        void validate (Doc doc) {
            try {
                validate (doc.getRawCommentText());
            } catch (Exception e) {
                throw new RuntimeException ("[" + doc.position().toString() + "] Javadoc must be valid XML: " + e.getMessage());
            }

        }

        void validate (String fragment) throws Exception {
            rootedFragment.setLength(0);
            rootedFragment.append ("<root>");
            rootedFragment.append (fragment);
            rootedFragment.append ("</root>");


            try {
                parser.parse(new InputSource(new StringReader (rootedFragment.toString())));
            } catch (SAXException e) {
                System.err.println(rootedFragment);
                throw e;
            }
        }


        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            exception.printStackTrace();
        }
    }

    /** convert 1-line <code>...</code> to <tt></tt> (keep multi-line code blocks as is) */
    public static void convertCode2TT (CharSequence text, StringBuffer sb) {
        assert text != sb;
        Pattern p = Pattern.compile("<code>([^<]*)</code>");
        Matcher m = p.matcher(text);

        sb.setLength(0);
        while (m.find()) {
             m.appendReplacement(sb, "<tt>$1</tt>");
        }
        m.appendTail(sb);
    }

    private static Pattern preBlockPattern = Pattern.compile("<pre>([^<]*)</pre>", Pattern.DOTALL); // in DOTALL mode '.' matches any character including \n
    
    /** convert <pre>...</pre> to <code>...</code>  */
    public static void convertPre2Code (CharSequence text, StringBuffer sb) {
        assert text != sb;
        Matcher m = preBlockPattern.matcher(text);

        sb.setLength(0);
        while (m.find()) {
             m.appendReplacement(sb, "<code>$1</code>");
        }
        m.appendTail(sb);
    }


}