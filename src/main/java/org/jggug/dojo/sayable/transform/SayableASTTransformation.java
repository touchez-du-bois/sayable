package org.jggug.dojo.sayable.transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PackageNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.objectweb.asm.Opcodes;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class SayableASTTransformation extends ClassCodeExpressionTransformer implements ASTTransformation, Opcodes {

    static final Class MY_CLASS = Sayable.class;
    static final ClassNode MY_TYPE = ClassHelper.make(MY_CLASS);
    static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();

    private SourceUnit sourceUnit;

    @Override
    public SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    private AnnotationNode annotationNode;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        sourceUnit = source;

        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new GroovyBugError("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes));
        }

        AnnotatedNode parent = (AnnotatedNode)nodes[1];
        AnnotationNode anno = (AnnotationNode)nodes[0];
        if (!MY_TYPE.equals(anno.getClassNode())) {
        	return;
        }
        annotationNode = anno;
        if (parent instanceof PackageNode) {
            visitPackage((PackageNode)parent);
        } else if (parent instanceof ClassNode) {
            visitClass((ClassNode)parent);
        } else if (parent instanceof MethodNode) {
            visitMethod((MethodNode)parent);
        }
    }

    @Override
    public Expression transform(Expression exp) {
        if (exp == null) {
        	return null;
        }
        if (exp instanceof MethodCallExpression) {
            MethodCallExpression methodCallExpression = (MethodCallExpression)exp;
            Expression objectExpression = methodCallExpression.getObjectExpression();

            if (objectExpression instanceof VariableExpression) {
            	VariableExpression variableExpression = (VariableExpression)objectExpression;
            	if ("this".equals(variableExpression.getName())) {
                    ConstantExpression methodConstant = (ConstantExpression)methodCallExpression.getMethod();
            		if ("println".equals(methodConstant.getText()) &&
                        hasArgument(methodCallExpression)) {
                        return createExcuteExpression(methodCallExpression);
            		}
            	}
            } else if (objectExpression instanceof PropertyExpression) {
                PropertyExpression propertyExpression = (PropertyExpression)objectExpression;
                ClassExpression object = (ClassExpression) propertyExpression.getObjectExpression();
                ConstantExpression property = (ConstantExpression)propertyExpression.getProperty();
                if ("java.lang.System".equals(object.getText())) {
                    if (("out".equals(property.getText()) || "err".equals(property.getText())) &&
                        hasArgument(methodCallExpression)) {
                        return createExcuteExpression(methodCallExpression);
                    }
                }
            }

            Expression object = transform(methodCallExpression.getObjectExpression());
            Expression method = transform(methodCallExpression.getMethod());
            Expression args = transform(methodCallExpression.getArguments());
            return new MethodCallExpression(object, method, args);
        } else if (exp instanceof ArgumentListExpression) {
        	ArgumentListExpression argumentListExpression = (ArgumentListExpression)exp;
            List<Expression> argumentList = new ArrayList<Expression>();
        	for (Expression argumentExpression : argumentListExpression.getExpressions()) {
        		argumentList.add(transform(argumentExpression));
        	}
        	return new ArgumentListExpression(argumentList);
        }
        return exp.transformExpression(this);
    }

    protected boolean hasArgument(MethodCallExpression methodCallExpression) {
        return !(((ArgumentListExpression)(methodCallExpression.getArguments())).getExpressions().isEmpty());
    }

    protected Expression createExcuteExpression(MethodCallExpression methodCallExpression) {
        Expression objectExpression = methodCallExpression.getObjectExpression();
        ConstantExpression methodConstant = (ConstantExpression)methodCallExpression.getMethod();

        StringBuilder sb = new StringBuilder("say ");
        setStringOption(sb, "voice", "--voice");
        setIntegerOption(sb, "rate", "--rate");
        setStringOption(sb, "outputFile", "--output-file");
        setStringOption(sb, "networkSend", "--network-send");
        setStringOption(sb, "audioDevice", "--audio-device");
        setStringOption(sb, "fileFormat", "--file-format");
        setStringOption(sb, "dataFormat", "--data-format");
        setIntegerOption(sb, "channels", "--channels");
        setIntegerOption(sb, "bitRate", "--bit-rate");
        setIntegerOption(sb, "quality", "--quality");

        Expression object = new BinaryExpression(
            new ConstantExpression(sb.toString()),
            Token.newSymbol("+", methodConstant.getLineNumber(), methodConstant.getColumnNumber()),
            ((ArgumentListExpression)(methodCallExpression.getArguments())).getExpressions().get(0)
        );
        Expression method = new ConstantExpression("execute");
        Expression args = new ArgumentListExpression();
        return new MethodCallExpression(object, method, args);
    }

    protected Object getMemberValue(AnnotationNode node, String name) {
        final Expression member = node.getMember(name);
        if (member != null && member instanceof ConstantExpression) {
            return ((ConstantExpression)member).getValue();
        }
        return null;
    }

    protected StringBuilder setStringOption(StringBuilder sb, String name, String option) {
        String value = (String)getMemberValue(annotationNode, name);
        if (value != null && !value.isEmpty()) {
            sb.append(option).append("=").append(value).append(" ");
        }
        return sb;
    }

    protected StringBuilder setIntegerOption(StringBuilder sb, String name, String option) {
        Integer value = (Integer)getMemberValue(annotationNode, name);
        if (value != null && value != -1) {
            sb.append(option).append("=").append(value).append(" ");
        }
        return sb;
    }
}