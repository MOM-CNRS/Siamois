package fr.siamois.infrastructure.config;

import fr.siamois.utils.SqlTimingStats;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Wraps a {@link DataSource} so every {@link Statement}/{@link PreparedStatement}/{@link CallableStatement}
 * it hands out has its {@code execute*} calls timed and reported to {@link SqlTimingStats} — temporary
 * diagnostic companion to {@link fr.siamois.ui.config.TimingPhaseListener}, since neither JSF phase timing
 * nor {@code RenderCallStats} says anything about time spent waiting on the database.
 * <p>
 * Installed by {@link SqlTimingDataSourcePostProcessor} rather than replacing the datasource bean directly,
 * so the actual pooling/driver configuration (Hikari, from {@code application*.yaml}) is untouched.
 * <p>
 * A single dynamic-proxy handler is reused at every level (DataSource → Connection → Statement): whichever
 * object a call returns gets wrapped again if it's one of those JDBC types, carrying along the SQL text
 * ({@code prepareStatement(sql, ...)}'s argument, or {@code execute(sql)}'s) so the eventual {@code execute*}
 * call can report what it ran.
 */
final class TimingDataSourceProxy implements InvocationHandler {

    private final Object target;
    private final String sql;

    private TimingDataSourceProxy(Object target, String sql) {
        this.target = target;
        this.sql = sql;
    }

    static DataSource wrap(DataSource dataSource) {
        return (DataSource) wrap(dataSource, null, DataSource.class);
    }

    private static Object wrap(Object target, String sql, Class<?> iface) {
        return Proxy.newProxyInstance(
                TimingDataSourceProxy.class.getClassLoader(),
                new Class<?>[]{iface},
                new TimingDataSourceProxy(target, sql));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        String effectiveSql = sql != null ? sql : firstStringArg(args);
        boolean timed = target instanceof Statement && effectiveSql != null && name.startsWith("execute");
        long start = timed ? System.nanoTime() : 0;

        Object result;
        try {
            result = method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            if (timed) {
                SqlTimingStats.record(effectiveSql, System.nanoTime() - start);
            }
        }

        if (result instanceof Connection connection) {
            return wrap(connection, null, Connection.class);
        }
        if (name.equals("prepareStatement") || name.equals("prepareCall")) {
            String stmtSql = firstStringArg(args);
            Class<?> iface = result instanceof CallableStatement ? CallableStatement.class : PreparedStatement.class;
            return wrap(result, stmtSql, iface);
        }
        if (result instanceof Statement && !(result instanceof PreparedStatement)) {
            return wrap(result, null, Statement.class);
        }
        return result;
    }

    private static String firstStringArg(Object[] args) {
        return (args != null && args.length > 0 && args[0] instanceof String s) ? s : null;
    }
}
