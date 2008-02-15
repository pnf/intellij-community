package com.intellij.application.options;

import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class JavaIndentOptionsProvider implements FileTypeIndentOptionsProvider {
  public CodeStyleSettings.IndentOptions createIndentOptions() {
    return new CodeStyleSettings.IndentOptions();
  }

  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  public IndentOptionsEditor createOptionsEditor() {
    return new JavaIndentOptionsEditor();
  }

  @NonNls
  public String getPreviewText() {
    return "public class Foo {\n" +
         "  public int[] X = new int[] { 1, 3, 5\n" +
         "  7, 9, 11};\n" +
         "  public void foo(boolean a, int x,\n" +
         "    int y, int z) {\n" +
         "    label1: do {\n" +
         "      try {\n" +
         "        if(x > 0) {\n" +
         "          int someVariable = a ? \n" +
         "             x : \n" +
         "             y;\n" +
         "        } else if (x < 0) {\n" +
         "          int someVariable = (y +\n" +
         "          z\n" +
         "          );\n" +
         "          someVariable = x = \n" +
         "          x +\n" +
         "          y;\n" +
         "        } else {\n" +
         "          label2:\n" +
         "          for (int i = 0;\n" +
         "               i < 5;\n" +
         "               i++) doSomething(i);\n" +
         "        }\n" +
         "        switch(a) {\n" +
         "          case 0: \n" +
         "           doCase0();\n" +
         "           break;\n" +
         "          default: \n" +
         "           doDefault();\n" +
         "        }\n" +
         "      }\n" +
         "      catch(Exception e) {\n" +
         "        processException(e.getMessage(),\n" +
         "          x + y, z, a);\n" +
         "      }\n" +
         "      finally {\n" +
         "        processFinally();\n" +
         "      }\n" +
         "    }while(true);\n" +
         "\n" +
         "    if (2 < 3) return;\n" +
         "    if (3 < 4)\n" +
         "       return;\n" +
         "    do x++ while (x < 10000);\n" +
         "    while (x < 50000) x++;\n" +
         "    for (int i = 0; i < 5; i++) System.out.println(i);\n" +
         "  }\n" +
         "  private class InnerClass implements I1,\n" +
         "  I2 {\n" +
         "    public void bar() throws E1,\n" +
         "     E2 {\n" +
         "    }\n" +
         "  }\n" +
         "}";
  }

  public void prepareForReformat(final PsiFile psiFile) {
    psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
  }
}
