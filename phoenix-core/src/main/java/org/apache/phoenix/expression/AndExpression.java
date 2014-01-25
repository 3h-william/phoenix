/*
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.expression;

import java.util.List;

import org.apache.phoenix.expression.visitor.ExpressionVisitor;


/**
 * 
 * AND expression implementation
 *
 * @author jtaylor
 * @since 0.1
 */
public class AndExpression extends AndOrExpression {
    private static final String AND = "AND";
    
    public static String combine(String expression1, String expression2) {
        if (expression1 == null) {
            return expression2;
        }
        if (expression2 == null) {
            return expression1;
        }
        return "(" + expression1 + ") " + AND + " (" + expression2 + ")";
    }
    
    public AndExpression() {
    }

    public AndExpression(List<Expression> children) {
        super(children);
    }

    @Override
    protected boolean getStopValue() {
        return Boolean.FALSE;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("(");
        for (int i = 0; i < children.size() - 1; i++) {
            buf.append(children.get(i) + " " + AND + " ");
        }
        buf.append(children.get(children.size()-1));
        buf.append(')');
        return buf.toString();
    }
    
    @Override
    public final <T> T accept(ExpressionVisitor<T> visitor) {
        List<T> l = acceptChildren(visitor, visitor.visitEnter(this));
        T t = visitor.visitLeave(this, l);
        if (t == null) {
            t = visitor.defaultReturn(this, l);
        }
        return t;
    }
}
