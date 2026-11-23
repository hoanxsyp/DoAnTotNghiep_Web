package com.webtro.modules.ai.engine;

import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.service.SystemConfigService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiModuleSupportTest {

    @Test
    void disabledModuleReturnsExplicitMaintenanceError() {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.getBoolean("ai.price.enabled")).thenReturn(false);
        AiModuleSupport support = new AiModuleSupport(config, Runnable::run);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> support.ensureEnabled("ai.price.enabled"));

        assertEquals(ErrorCode.AI_MODULE_DISABLED, exception.getErrorCode());
    }

    @Test
    void timeoutReturnsServiceUnavailableInsteadOfLeakingInfrastructureError() {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.getInt("ai.price.timeout_ms")).thenReturn(1);
        Executor neverCompletes = command -> { };
        AiModuleSupport support = new AiModuleSupport(config, neverCompletes);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> support.runWithTimeout("price", "ai.price.timeout_ms", () -> "result"));

        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE, exception.getErrorCode());
    }
}
