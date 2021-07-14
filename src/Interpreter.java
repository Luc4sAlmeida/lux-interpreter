import java.util.List;

class Interpreter implements Expr.Visitor<Object>,
        Stmt.Visitor<Void> {

    private Environment environment = new Environment();

    void interpret(List<Stmt> statements, boolean REPL) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lux.runtimeError(error);
        }
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    void executeBlock(List<Stmt> statements,
                      Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            parseDoubleToString((double) object);
        }

        return object.toString();
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }


    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
            case GREATER_EQUAL:
            case LESS:
            case LESS_EQUAL:
                return checkCompOperands(expr.operator, left, right);
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case PLUS:
                return performPlusOperator(expr.operator, left, right);
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
        }

        // Unreachable.
        return null;
    }

    private boolean checkCompOperands(Token operator, Object left, Object right) {
        if(left instanceof String && right instanceof String) {
            switch(operator.type) {
                case GREATER:
                    return ((String) left).compareTo((String)right) > 0;
                case GREATER_EQUAL:
                    return ((String) left).compareTo((String)right) >= 0;
                case LESS:
                    return ((String) left).compareTo((String)right) < 0;
                case LESS_EQUAL:
                    return ((String) left).compareTo((String)right) <= 0;
            }
        }
        if(left instanceof Double && right instanceof Double) {
            switch(operator.type) {
                case GREATER:
                    return (double) left > (double) right;
                case GREATER_EQUAL:
                    return (double) left >= (double) right;
                case LESS:
                    return (double) left < (double) right;
                case LESS_EQUAL:
                    return (double) left <= (double) right;
            }
        }
        throw new RuntimeError(operator, "Operands must be numbers or strings.");
    }

    Object performPlusOperator(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return (double)left + (double)right;
        }
        if (left instanceof String && right instanceof String) {
            return (String)left + (String)right;
        }
        if(left instanceof String && right instanceof Double) {
            return (String)left + (String)parseDoubleToString((double)right);
        }
        if(left instanceof Double && right instanceof String) {
            return (String)parseDoubleToString((double)left) + (String)right;
        }
        throw new RuntimeError(operator,
                "Operands must be two numbers or two strings.");
    }

    private String parseDoubleToString(Double value) {
        String string = value.toString();
        if(string.endsWith(".0")) {
            string = string.substring(0, string.length() - 2);
        }
        return string;
    }

    private void checkNumberOperands(Token operator,
                                     Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        // Unreachable.
        return null;
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

}