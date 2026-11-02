package org.ruoyi.service.operator;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.domain.entity.operator.OperatorProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库行 → 生效快照的映射规则。
 *
 * <p>重点守两件事：半填的行不能把配置项清成空值；字符型布尔必须按 1/0 语义解析。
 */
@Tag("dev")
class OperatorEffectiveProfileTest {

    private static OperatorProfileProperties properties() {
        OperatorProfileProperties properties = new OperatorProfileProperties();
        properties.setEnabled(false);
        properties.setPromptMode("native");
        properties.setIdentityName("配置身份");
        properties.setIdentityRule("配置规则");
        properties.setInjectText("CONFIG-INJECT");
        properties.setWorkspaceInstructionsAuthoritative(true);
        properties.setAntiRefusalTail(true);
        properties.setRefusalRetryMax(2);
        properties.setApprovalPolicy("NEVER");
        properties.setPermissionMode("FULL_ACCESS");
        properties.setForcePermissionMode(true);
        properties.setBypassPermissionChecks(false);
        return properties;
    }

    @Test
    void configDerivationKeepsModeAndFloorsRetry() {
        OperatorProfileProperties properties = properties();
        properties.setPromptMode("OPERATOR");
        properties.setRefusalRetryMax(-5);

        OperatorEffectiveProfile profile = OperatorEffectiveProfile.from(properties);

        assertEquals("operator", profile.promptMode());
        assertEquals(0, profile.refusalRetryMax(), "negative retry budget must floor at zero");
        assertEquals("config", profile.source());
    }

    @Test
    void unknownPromptModeFallsBackToNative() {
        OperatorProfileProperties properties = properties();
        properties.setPromptMode("whatever");

        assertEquals("native", OperatorEffectiveProfile.from(properties).promptMode());
    }

    @Test
    void partialRowFallsBackToConfiguration() {
        OperatorProfile row = new OperatorProfile();
        row.setId(42L);
        row.setEnabled("1");
        row.setScopeType("1");
        row.setScopeId("000000");
        // 只填身份，其余留空：必须回落到配置默认，不能把审批策略清成 null
        row.setIdentityName("库里的身份");

        OperatorEffectiveProfile profile = OperatorEffectiveProfile.from(row, properties());

        assertTrue(profile.enabled(), "enabled=1 must be parsed as true");
        assertEquals("库里的身份", profile.identityName());
        assertEquals("配置规则", profile.identityRule(), "blank field must fall back to config");
        assertEquals("CONFIG-INJECT", profile.injectText());
        assertEquals("NEVER", profile.approvalPolicy());
        assertEquals("FULL_ACCESS", profile.permissionMode());
        assertEquals(2, profile.refusalRetryMax());
        assertTrue(profile.forcePermissionMode());
        assertEquals("database:42", profile.source());
    }

    @Test
    void explicitZeroFlagsOverrideConfiguration() {
        OperatorProfile row = new OperatorProfile();
        row.setId(9L);
        row.setEnabled("1");
        row.setPromptMode("operator");
        row.setAntiRefusalTail("0");
        row.setWorkspaceAuthoritative("0");
        row.setForcePermissionMode("0");
        row.setBypassPermissionChecks("1");
        row.setRefusalRetryMax(0);

        OperatorEffectiveProfile profile = OperatorEffectiveProfile.from(row, properties());

        assertFalse(profile.antiRefusalTail());
        assertFalse(profile.workspaceAuthoritative());
        assertFalse(profile.forcePermissionMode());
        assertTrue(profile.bypassPermissionChecks());
        assertEquals(0, profile.refusalRetryMax());
        assertEquals("operator", profile.promptMode());
    }

    @Test
    void nullRowMeansConfiguration() {
        assertEquals("config", OperatorEffectiveProfile.from(null, properties()).source());
    }

    @Test
    void truthySpellingsAreAccepted() {
        OperatorProfile row = new OperatorProfile();
        row.setEnabled("true");
        row.setForcePermissionMode("Y");

        OperatorEffectiveProfile profile = OperatorEffectiveProfile.from(row, properties());

        assertTrue(profile.enabled());
        assertTrue(profile.forcePermissionMode());
    }
}
