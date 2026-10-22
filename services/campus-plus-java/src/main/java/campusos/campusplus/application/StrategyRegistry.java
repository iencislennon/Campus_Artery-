package campusos.campusplus.application;

import campusos.campusplus.domain.SchedulingStrategy;
import campusos.common.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Runtime lookup of scheduling strategies. A new algorithm = one new class + one register() call. */
public final class StrategyRegistry {
    private final Map<String, SchedulingStrategy> strategies = new LinkedHashMap<>();

    public StrategyRegistry register(SchedulingStrategy strategy) {
        strategies.put(strategy.name(), strategy);
        return this;
    }

    public SchedulingStrategy get(String name) {
        SchedulingStrategy strategy = strategies.get(name);
        if (strategy == null) throw ApiException.invalid("Unknown scheduling strategy '" + name + "'. Use one of " + names() + ".");
        return strategy;
    }

    public Set<String> names() { return strategies.keySet(); }
}
