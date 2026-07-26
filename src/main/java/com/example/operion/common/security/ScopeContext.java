package com.example.operion.common.security;

import com.example.operion.module.auth.enums.Scope;

public class ScopeContext {

  private static final ThreadLocal<String> currentScope = new ThreadLocal<>();

  public static void setScope(String scope) {
    currentScope.set(scope);
  }

  public static String getScope() {
    return currentScope.get();
  }

  public static void clear() {
    currentScope.remove();
  }

  public static boolean hasAtLeast(Scope minimum) {

    String value = currentScope.get();

    if (value == null) {
      return false;
    }

    Scope current;

    try {
      current = Scope.valueOf(value);
    } catch (IllegalArgumentException e) {
      return false;
    }

    return current.ordinal() >= minimum.ordinal();
  }
}
