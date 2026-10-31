package org.ruoyi.service.chat;

import org.junit.jupiter.api.*;
import org.ruoyi.controller.chat.ChatSessionController;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.chat.ChatSessionBo;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatSessionAccessTest {
    @Test void cannotReadEditOrDeleteAnotherUsersSession() {
        var sessions = mock(IChatSessionService.class);
        var guard = new ChatSessionOwnershipGuard(sessions, mock(org.ruoyi.service.learning.CourseMaterialService.class));
        var controller = new ChatSessionController(sessions, guard);
        var other = new ChatSessionVo(); other.setId(20L); other.setUserId(2L);
        when(sessions.queryById(20L)).thenReturn(other);
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);
            assertThrows(IllegalArgumentException.class, () -> controller.getInfo(20L));
            var bo = new ChatSessionBo(); bo.setId(20L); bo.setChatMode("debugging");
            assertThrows(IllegalArgumentException.class, () -> controller.edit(bo));
            assertThrows(IllegalArgumentException.class, () -> controller.remove(new Long[]{20L}));
            verify(sessions, never()).updateByBo(any());
            verify(sessions, never()).deleteWithValidByIds(any(), any());
        }
    }
    @Test void editingCannotTransferSessionOwnership() {
        var sessions = mock(IChatSessionService.class);
        var owned = new ChatSessionVo(); owned.setId(20L); owned.setUserId(1L);
        when(sessions.queryById(20L)).thenReturn(owned); when(sessions.updateByBo(any())).thenReturn(true);
        var controller = new ChatSessionController(sessions, new ChatSessionOwnershipGuard(sessions, mock(org.ruoyi.service.learning.CourseMaterialService.class)));
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);
            var bo = new ChatSessionBo(); bo.setId(20L); bo.setUserId(99L); bo.setChatMode("debugging");
            controller.edit(bo); assertEquals(1L, bo.getUserId());
        }
    }
}
