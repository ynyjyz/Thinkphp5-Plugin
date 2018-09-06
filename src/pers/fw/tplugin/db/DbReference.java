package pers.fw.tplugin.db;

import com.intellij.codeInsight.lookup.*;
import com.intellij.database.model.DasColumn;
import com.intellij.database.model.DasTable;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.JBIterable;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.*;
import icons.DatabaseIcons;
import pers.fw.tplugin.inter.GotoCompletionContributor;
import pers.fw.tplugin.inter.GotoCompletionLanguageRegistrar;
import pers.fw.tplugin.inter.GotoCompletionProvider;
import pers.fw.tplugin.inter.GotoCompletionRegistrarParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.fw.tplugin.util.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class DbReference implements GotoCompletionLanguageRegistrar {
    private static MethodMatcher.CallToSignature[] QUERY = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "where"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "group"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "avg"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "count"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "sum"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "max"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "min"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "field"),
    };

    private static MethodMatcher.CallToSignature[] QUERYARR = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "delete"),
            new MethodMatcher.CallToSignature("\\think\\Model", "delete"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "update"),
            new MethodMatcher.CallToSignature("\\think\\Model", "update"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "insert"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "where"),
    };

    private static MethodMatcher.CallToSignature[] QUERYTABLE = new MethodMatcher.CallToSignature[]{
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "join"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "table"),
            new MethodMatcher.CallToSignature("\\think\\Db", "table"),
            new MethodMatcher.CallToSignature("\\think\\db\\Query", "name"),
            new MethodMatcher.CallToSignature("\\think\\Db", "name"),
    };

    @Override
    public boolean support(@NotNull Language language) {
        return PhpLanguage.INSTANCE == language;
    }

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement(), new GotoCompletionContributor() {
            @Nullable
            @Override
            public GotoCompletionProvider getProvider(@Nullable PsiElement psiElement) {
                if (psiElement == null) {
                    return null;
                }
                PsiElement param = psiElement.getParent();
//                PsiElement arr=null;
//                try{
//                    arr=param.getParent().getParent();
//                }catch (Exception e){}
                if (param == null) return null;
                if ((param instanceof StringLiteralExpression && (MethodMatcher.getMatchedSignatureWithDepth(param, QUERY, 0) != null) ||
                        MethodMatcher.getMatchedSignatureWithDepth(param, new MethodMatcher.CallToSignature[]{
                                new MethodMatcher.CallToSignature("\\think\\db\\Query", "join")}, 1) != null)
                ) {
                    //列
                    return new ColumnProvider(param);
                } else if (PsiElementUtil.isFunctionReference(param, "db", 0) || MethodMatcher.getMatchedSignatureWithDepth(param, QUERYTABLE, 0) != null) {
                    //表
                    return new TableProvider(param);
//                }else if(arr instanceof ArrayAccessExpression){
//                    String text = arr.getText();
//                    if(text!=null){
//                        if(text.startsWith("$where[")||text.startsWith("$params[")){
//                            return new ColumnProvider(param);
//                        }
//                    }
                } else {
                    //列, 数组里的列
                    PsiElement param1 = null;
                    try {
                        param1 = param.getParent().getParent();
                        if (!(param1 instanceof ArrayCreationExpression))
                            param1 = param1.getParent();
                        if (!(param1 instanceof ArrayCreationExpression))
                            return null;
                    } catch (Exception e) {
                        return null;
                    }
                    if (MethodMatcher.getMatchedSignatureWithDepth(param1, QUERYARR, 0) != null) {
                        return new ColumnProvider(param1);
                    }
                }

                return null;
            }
        });
    }

    public static class ColumnProvider extends GotoCompletionProvider {
        public ColumnProvider(PsiElement element) {
            super(element);
        }

        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            final Collection<LookupElement> lookupElements = new ArrayList<>();

            PsiElement paramList = getElement().getParent();
            if (paramList == null) return lookupElements;
            PsiElement methodRef = paramList.getParent();
            if (!(methodRef instanceof MethodReference)) return lookupElements;

            //获取方法对象的类
            int type = 1;
            String tableName = "";
//            PsiElement resolve = ((MethodReference) methodRef).resolve();
            //Query类
//            PhpClassImpl phpClass = Util.getPhpClass(resolve);
            //Model子类
            PhpClass phpClass = Util.getInstanseClass(getElement().getProject(), (MethodReference) methodRef);
            if (phpClass != null) {
                Collection<Field> fields = phpClass.getFields();
                for (Field item : fields) {
//                    Tool.log(item.getName() + ": " + item.getDefaultValue().getText()+": "+item.getDefaultValuePresentation());
                    if ("name".equals(item.getName())) {
                        String name = item.getDefaultValue().getText();
                        if (name != null && !name.isEmpty()) {
                            tableName = name;
                            break;
                        }
                    } else if ("table".equals(item.getName())) {
                        String name = item.getDefaultValue().getText();//item.getDefaultValuePresentation();
                        if (name != null && !name.isEmpty()) {
                            type = 2;
                            tableName = name;
                            break;
                        }
                    }
                }
            }

            //上下文表
            JBIterable<? extends DasColumn> columns = DbTableUtil.getColumns(getElement().getProject(), tableName, type);
            if (columns != null) {
                for (DasColumn item : columns) {
                    String comment = "";
                    if (item.getComment() != null)
                        comment = item.getComment();
                    lookupElements.add(LookupElementBuilder.create(item.getName()).withTailText("   " + comment).withIcon(DatabaseIcons.Col));
                }
            }

            //join和alias表
            Map<String, String> alias = DbTableUtil.getAlias(getElement(), tableName); //键为前缀, 值为表
            if (alias != null) {
                for (String key : alias.keySet()) {
                    JBIterable<? extends DasColumn> aliasColumns = DbTableUtil.getColumns(getElement().getProject(), alias.get(key), 2);
                    if (aliasColumns != null) {
                        for (DasColumn item : aliasColumns) {
                            String comment = "";
                            if (item.getComment() != null)
                                comment = item.getComment();
                            lookupElements.add(LookupElementBuilder.create(key + "." + item.getName())
                                    .withTailText("   " + comment).withIcon(DatabaseIcons.Col));
                        }
                    }
                }
            }

            return lookupElements;
        }

        @NotNull
        public Collection<? extends PsiElement> getPsiTargets(@NotNull PsiElement psiElement, int offset, @NotNull Editor editor) {

            return null;
        }
    }

    private static class TableProvider extends GotoCompletionProvider {
        public TableProvider(PsiElement element) {
            super(element);
        }

        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            final Collection<LookupElement> lookupElements = new ArrayList<>();
            JBIterable<? extends DasTable> tables = DbTableUtil.getTables(getElement().getProject());
            for (DasTable table : tables) {
                String comment = "";
                if (table.getComment() != null)
                    comment = table.getComment();
                lookupElements.add(LookupElementBuilder.create(table.getName()).withTailText("   " + comment).withIcon(DatabaseIcons.Table));
            }
            return lookupElements;
        }

        @NotNull
        public Collection<? extends PsiElement> getPsiTargets(@NotNull PsiElement psiElement, int offset, @NotNull Editor editor) {
            return null;
        }
    }
}