package com.example.operion.module.expense.enums;

/**
 * BE-19: a label for visibility, not a charge. HQ = spending the branch does for
 * HQ-related needs; BRANCH_SPECIFIC = its own running costs. The branch pays both,
 * and both count against its monthly budget.
 */
public enum ExpenseCategory {

    HQ,
    BRANCH_SPECIFIC
}
