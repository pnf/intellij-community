package com.jetbrains.python;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 * @author Keith Lea
 */
public class PythonParserDefinition implements ParserDefinition {
  private final PythonLanguage language;

  private final TokenSet myWhitespaceTokens;
  private final TokenSet myCommentTokens;

  public PythonParserDefinition() {
    language = (PythonLanguage)PythonFileType.INSTANCE.getLanguage();

    myWhitespaceTokens = TokenSet.create(PyTokenTypes.LINE_BREAK, PyTokenTypes.SPACE, PyTokenTypes.TAB, PyTokenTypes.FORMFEED);
    myCommentTokens = TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT);
  }

  @NotNull
  public Lexer createLexer(Project project) {
    return new PythonIndentingLexer();
  }

  public IFileElementType getFileNodeType() {
    return language.getFileElementType();
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return myWhitespaceTokens;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return myCommentTokens;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public PsiParser createParser(Project project) {
    return new PyParser();
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof PyElementType) {
      PyElementType pyElType = (PyElementType)type;
      return pyElType.createElement(node);
    }
    else if (type instanceof PyStubElementType) {
      return ((PyStubElementType)type).createElement(node);
    }

    return new ASTWrapperPsiElement(node);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PyFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    if (left.getElementType() == PyTokenTypes.END_OF_LINE_COMMENT) return SpaceRequirements.MUST_LINE_BREAK;
    if (right.getElementType() == PyTokenTypes.DEF_KEYWORD || right.getElementType() == PyTokenTypes.CLASS_KEYWORD) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    if (left.getElementType() == TokenType.WHITE_SPACE || right.getElementType() == TokenType.WHITE_SPACE) {
      return SpaceRequirements.MAY;
    }
    final PyStatement leftStatement = PsiTreeUtil.getParentOfType(left.getPsi(), PyStatement.class);
    if (leftStatement != null && !PsiTreeUtil.isAncestor(leftStatement, right.getPsi(), false)) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    final Lexer lexer = createLexer(left.getPsi().getProject());
    return LanguageUtil.canStickTokensTogetherByLexer(left, right, lexer);
  }

}
