package cn.rhymed.data.turbo;

import cn.rhymed.data.turbo.config.PageConfig;
import cn.rhymed.data.turbo.domain.PageResult;
import cn.rhymed.data.turbo.utils.StrUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * SQL解析器，处理ROW_NUMBER分页等复杂SQL
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:45
 **/
@Slf4j
public class RowNumberSqlParser {

    public static String getRowNumberSql(String sql, PageConfig config) {
        Select select = getStatement(sql);
        sqlToRowNumber(select, config);
        return select.toString();
    }

    public static String getRowNumberPageSql(String sql, PageConfig config, PageResult pageResult) {
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (Throwable throwable) {
            log.error("Failed to parse sql: {}", sql, throwable);
            throw new RuntimeException("Failed to parse sql", throwable);
        }

        // 如果是 DELETE 语句，直接在 DELETE 上添加分页条件
        if (stmt instanceof Delete) {
            Delete delete = (Delete) stmt;
            addPageConditionToDelete(delete, config, pageResult);
            return delete.toString();
        } else if (stmt instanceof Update) {
            // 如果是 UPDATE 语句，直接在 UPDATE 上添加分页条件
            Update update = (Update) stmt;
            addPageConditionToUpdate(update, config, pageResult);
            return update.toString();
        } else if (stmt instanceof Select) {
            // 如果是 SELECT 语句，使用原来的逻辑
            Select select = (Select) stmt;
            sqlToRowNumberPage(select, config, pageResult);
            return select.toString();
        } else {
            throw new RuntimeException("Unsupported SQL statement type: " + stmt.getClass().getName());
        }
    }

    /**
     * 在 DELETE 语句上添加分页条件（BETWEEN ... AND ...）
     */
    private static void addPageConditionToDelete(Delete delete, PageConfig config, PageResult pageResult) {
        String name = config.getPrimaryId();
        // 如果没指定主键ID，尝试从表名获取
        if (StrUtil.isBlank(name)) {
            Table table = delete.getTable();
            String alias = table.getAlias() != null ? table.getAlias().getName() : null;
            name = StrUtil.isBlank(alias) ? "id" : alias + ".id";
        }

        // 构建 BETWEEN 条件
        Between between = new Between();
        between.setLeftExpression(new Column(name));
        between.setBetweenExpressionStart(new LongValue(pageResult.getStartKey()));
        between.setBetweenExpressionEnd(new LongValue(pageResult.getEndKey()));

        // 将 BETWEEN 条件添加到 WHERE 子句
        if (delete.getWhere() == null) {
            delete.setWhere(between);
        } else {
            Expression where = delete.getWhere();
            AndExpression andExpression = new AndExpression(where, between);
            delete.setWhere(andExpression);
        }
    }

    /**
     * 在 UPDATE 语句上添加分页条件（BETWEEN ... AND ...）
     */
    private static void addPageConditionToUpdate(Update update, PageConfig config, PageResult pageResult) {
        String name = config.getPrimaryId();
        // 如果没指定主键ID，尝试从表名获取
        if (StrUtil.isBlank(name)) {
            Table table = update.getTable();
            String alias = table.getAlias() != null ? table.getAlias().getName() : null;
            name = StrUtil.isBlank(alias) ? "id" : alias + ".id";
        }

        // 构建 BETWEEN 条件
        Between between = new Between();
        between.setLeftExpression(new Column(name));
        between.setBetweenExpressionStart(new LongValue(pageResult.getStartKey()));
        between.setBetweenExpressionEnd(new LongValue(pageResult.getEndKey()));

        // 将 BETWEEN 条件添加到 WHERE 子句
        if (update.getWhere() == null) {
            update.setWhere(between);
        } else {
            Expression where = update.getWhere();
            AndExpression andExpression = new AndExpression(where, between);
            update.setWhere(andExpression);
        }
    }


    public static Select getStatement(String sql) {
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (Throwable throwable) {
            log.error("Failed to parse sql: {}", sql, throwable);
            throw new RuntimeException("Failed to parse sql", throwable);
        }

        Select select;

        // 如果是 DELETE 语句，转换为 SELECT 语句
        if (stmt instanceof Delete) {
            select = convertDeleteToSelect((Delete) stmt);
        } else if (stmt instanceof Update) {
            // 如果是 UPDATE 语句，转换为 SELECT 语句
            select = convertUpdateToSelect((Update) stmt);
        } else if (stmt instanceof Select) {
            select = (Select) stmt;
        } else {
            throw new RuntimeException("Unsupported SQL statement type: " + stmt.getClass().getName());
        }

        SelectBody selectBody = select.getSelectBody();

        try {
            processSelectBody(selectBody);
        } catch (Exception e) {
            log.error("Failed to parse sql: {}", sql, e);
            throw new RuntimeException("Failed to parse sql", e);
        }

        processWithItemsList(select.getWithItemsList());
        return select;
    }

    /**
     * 将 DELETE 语句转换为 SELECT 语句
     * DELETE FROM table WHERE ... -> SELECT * FROM table WHERE ...
     */
    private static Select convertDeleteToSelect(Delete delete) {
        PlainSelect plainSelect = new PlainSelect();

        // 设置 SELECT *
        plainSelect.addSelectItems(new AllColumns());

        // 复制 FROM 子句
        plainSelect.setFromItem(delete.getTable());

        // 复制 WHERE 条件
        if (delete.getWhere() != null) {
            plainSelect.setWhere(delete.getWhere());
        }

        // 复制 JOIN（如果有）
        if (delete.getJoins() != null) {
            plainSelect.setJoins(delete.getJoins());
        }

        Select select = new Select();
        select.setSelectBody(plainSelect);

        return select;
    }

    /**
     * 将 UPDATE 语句转换为 SELECT 语句
     * UPDATE table SET ... WHERE ... -> SELECT * FROM table WHERE ...
     */
    private static Select convertUpdateToSelect(Update update) {
        PlainSelect plainSelect = new PlainSelect();

        // 设置 SELECT *
        plainSelect.addSelectItems(new AllColumns());

        // 复制 FROM 子句
        plainSelect.setFromItem(update.getTable());

        // 复制 WHERE 条件
        if (update.getWhere() != null) {
            plainSelect.setWhere(update.getWhere());
        }

        // 复制 JOIN（如果有）
        if (update.getJoins() != null) {
            plainSelect.setJoins(update.getJoins());
        }

        Select select = new Select();
        select.setSelectBody(plainSelect);

        return select;
    }

    public static String getTableAlias(Select select) {
        SelectBody selectBody = select.getSelectBody();

        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            FromItem fromItem = plainSelect.getFromItem();

            if (fromItem instanceof Table) {
                Table table = (Table) fromItem;
                Alias alias = table.getAlias();
                return alias != null ? alias.getName() : null;
            } else if (fromItem instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) fromItem;
                Alias alias = subSelect.getAlias();
                return alias != null ? alias.getName() : null;
            }
        }

        return null;
    }

    private static void sqlToRowNumberPage(Select select, PageConfig config, PageResult pageResult) {
        String name = config.getPrimaryId();
        // 如果没指定主键ID 则使用表的第一个别名id
        if (StrUtil.isBlank(name)) {
            String alias = getTableAlias(select);
            name = StrUtil.isBlank(alias) ? "id" : alias + ".id";
        }
        PlainSelect selectBody = (PlainSelect) select.getSelectBody();

        Between between = new Between();
        between.setLeftExpression(new Column(name));
        between.setBetweenExpressionStart(new LongValue(pageResult.getStartKey()));
        between.setBetweenExpressionEnd(new LongValue(pageResult.getEndKey()));
        if (selectBody.getWhere() == null) {
            selectBody.setWhere(between);
        } else {
            Expression where = selectBody.getWhere();
            AndExpression andExpression = new AndExpression(where, between);
            selectBody.setWhere(andExpression);
        }
        // 设置排序
        OrderByElement orderBy = new OrderByElement();
        orderBy.setExpression(new Column(name));
        orderBy.setAsc(true);
        selectBody.setOrderByElements(Collections.singletonList(orderBy));
    }

    private static void sqlToRowNumber(Select select, PageConfig config) {
        String name = config.getPrimaryId();
        // 如果没指定主键ID 则使用表的第一个别名id
        if (StrUtil.isBlank(name)) {
            String alias = getTableAlias(select);
            name = StrUtil.isBlank(alias) ? "id" : alias + ".id";
        }

        // 如果分页大小设置不合理则强制为1000
        int pageSize = config.getPageSize();
        if (pageSize <= 1) {
            pageSize = 1000;
        }


        SelectBody selectBody = select.getSelectBody();
        List<SelectItem> subCountItem = new ArrayList<>();
        subCountItem.add(new SelectExpressionItem(new Column(name + " AS id")));
        subCountItem.add(new SelectExpressionItem(new Column("row_number() OVER ( ORDER BY " + name + " ) AS row_num ")));
        ((PlainSelect) selectBody).setSelectItems(subCountItem);
        PlainSelect plainSelect = new PlainSelect();
        SubSelect subSelect = new SubSelect();
        subSelect.setSelectBody(selectBody);
        subSelect.setAlias(new Alias("t"));
        plainSelect.setFromItem(subSelect);

        // 设置查询条件
        List<SelectItem> pageCountItem = new ArrayList<>();
        pageCountItem.add(new SelectExpressionItem(new Column("floor(( row_num - 1 ) / " + pageSize + " )  AS page_num")));
        pageCountItem.add(new SelectExpressionItem(new Column("min( id ) AS start_key")));
        pageCountItem.add(new SelectExpressionItem(new Column("max( id ) AS end_key")));
        pageCountItem.add(new SelectExpressionItem(new Column("count(*) AS page_size")));
        plainSelect.setSelectItems(pageCountItem);

        // 设置分组条件
        Column groupByColumn = new Column("page_num");
        GroupByElement groupBy = new GroupByElement();
        groupBy.addGroupByExpressions(groupByColumn);
        plainSelect.setGroupByElement(groupBy);

        // 设置排序
        OrderByElement orderBy = new OrderByElement();
        orderBy.setExpression(new Column("id"));
        orderBy.setAsc(true);
        plainSelect.setOrderByElements(Collections.singletonList(orderBy));

        select.setSelectBody(plainSelect);
    }

    private static void processSelectBody(SelectBody selectBody) {
        if (selectBody instanceof PlainSelect) {
            processPlainSelect((PlainSelect) selectBody);
        } else if (selectBody instanceof WithItem) {
            WithItem withItem = (WithItem) selectBody;
            if (withItem.getSubSelect() != null && withItem.getSubSelect().getSelectBody() != null) {
                processSelectBody(withItem.getSubSelect().getSelectBody());
            }
        } else {
            SetOperationList operationList = (SetOperationList) selectBody;
            if (operationList.getSelects() != null && !operationList.getSelects().isEmpty()) {
                List<SelectBody> plainSelects = operationList.getSelects();

                for (SelectBody plainSelect : plainSelects) {
                    processSelectBody(plainSelect);
                }
            }

            if (orderByHashParameters(operationList.getOrderByElements())) {
                operationList.setOrderByElements(null);
            }
        }
    }

    private static void processPlainSelect(PlainSelect plainSelect) {
        if (orderByHashParameters(plainSelect.getOrderByElements())) {
            plainSelect.setOrderByElements(null);
        }

        if (plainSelect.getFromItem() != null) {
            processFromItem(plainSelect.getFromItem());
        }

        if (plainSelect.getJoins() != null && !plainSelect.getJoins().isEmpty()) {
            List<Join> joins = plainSelect.getJoins();
            for (Join join : joins) {
                if (join.getRightItem() != null) {
                    processFromItem(join.getRightItem());
                }
            }
        }
    }

    public static void processWithItemsList(List<WithItem> withItemsList) {
        if (withItemsList != null && !withItemsList.isEmpty()) {
            for (WithItem item : withItemsList) {
                processSelectBody(item.getSubSelect().getSelectBody());
            }
        }
    }

    public static void processFromItem(FromItem fromItem) {
        if (fromItem instanceof SubJoin) {
            SubJoin subJoin = (SubJoin) fromItem;
            if (subJoin.getJoinList() != null && !subJoin.getJoinList().isEmpty()) {
                for (Join join : subJoin.getJoinList()) {
                    if (join.getRightItem() != null) {
                        processFromItem(join.getRightItem());
                    }
                }
            }

            if (subJoin.getLeft() != null) {
                processFromItem(subJoin.getLeft());
            }
        } else if (fromItem instanceof ValuesList) {
            log.trace("ignore ValuesList");
        } else if (fromItem instanceof LateralSubSelect) {
            LateralSubSelect lateralSubSelect = (LateralSubSelect) fromItem;
            if (lateralSubSelect.getSubSelect() != null) {
                SubSelect subSelect = lateralSubSelect.getSubSelect();
                if (subSelect.getSelectBody() != null) {
                    processSelectBody(subSelect.getSelectBody());
                }
            }
        }

    }

    public static boolean orderByHashParameters(List<OrderByElement> orderByElements) {
        if (orderByElements == null) {
            return true;
        } else {
            Iterator<OrderByElement> var2 = orderByElements.iterator();

            OrderByElement orderByElement;
            do {
                if (!var2.hasNext()) {
                    return true;
                }

                orderByElement = var2.next();
            } while (!orderByElement.toString().contains("?"));

            return false;
        }
    }

}
