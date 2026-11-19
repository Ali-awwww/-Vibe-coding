package com.example.util

import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFile
import com.example.domain.model.WorkspaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewHtmlSecurityPolicyTest {
    @Test
    fun workspaceOriginsAreStableAndIsolated() {
        val first = PreviewHtmlSecurityPolicy.baseUrlForWorkspace("workspace-1")
        assertEquals(first, PreviewHtmlSecurityPolicy.baseUrlForWorkspace("workspace-1"))
        assertNotEquals(first, PreviewHtmlSecurityPolicy.baseUrlForWorkspace("workspace-2"))
        assertTrue(first.matches(Regex("https://[a-f0-9]{32}\\.preview\\.invalid/")))
    }

    @Test
    fun contentSecurityPolicyIsInsertedBeforeWorkspaceScripts() {
        val html = """<html><head><script>window.previewReady = true;</script></head><body></body></html>"""
        val secured = PreviewHtmlSecurityPolicy.injectContentSecurityPolicy(html)
        assertTrue(secured.indexOf("Content-Security-Policy") < secured.indexOf("<script>"))
        assertTrue(secured.contains("connect-src 'none'"))
        assertTrue(secured.contains("frame-src 'none'"))
        assertTrue(secured.contains("object-src 'none'"))
        assertTrue(secured.contains("form-action 'none'"))
    }

    @Test
    fun contentSecurityPolicyIsAddedWhenHeadIsMissing() {
        val secured = PreviewHtmlSecurityPolicy.injectContentSecurityPolicy("<html><body><p>Preview</p></body></html>")
        assertTrue(secured.indexOf("Content-Security-Policy") < secured.indexOf("<body>"))
    }

    @Test
    fun networkLocalFileAndContentRequestsAreBlocked() {
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockResourceRequest("https://example.com/script.js"))
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockResourceRequest("http://127.0.0.1:3000/"))
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockResourceRequest("file:///etc/passwd"))
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockResourceRequest("content://provider/private"))
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockResourceRequest("/src/main.js"))
        assertFalse(PreviewHtmlSecurityPolicy.shouldBlockResourceRequest("data:image/png;base64,AA=="))
        assertFalse(PreviewHtmlSecurityPolicy.shouldBlockResourceRequest("blob:https://preview.invalid/id"))
    }

    @Test
    fun topLevelNavigationIsBlockedExceptBlankReset() {
        assertFalse(PreviewHtmlSecurityPolicy.shouldBlockNavigation("about:blank"))
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockNavigation("https://example.com"))
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockNavigation("javascript:alert(1)"))
        assertTrue(PreviewHtmlSecurityPolicy.shouldBlockNavigation("intent://example"))
    }

    @Test
    fun generatedIndexHtmlReceivesPolicyBeforeConsoleBridgeScript() {
        val workspace = Workspace(id = "workspace-1", name = "Preview", type = WorkspaceType.WEB_APP)
        val index = WorkspaceFile(
            id = "index",
            workspaceId = workspace.id,
            name = "index.html",
            path = "index.html",
            extension = "html",
            content = "<html><head></head><body><h1>Preview</h1></body></html>"
        )
        val html = WorkspaceSandboxEngine.generatePreviewHtml(workspace, listOf(index))
        assertTrue(html.indexOf("Content-Security-Policy") < html.indexOf("<script>"))
        assertTrue(html.contains("connect-src 'none'"))
    }

    @Test
    fun missingEntryPointDoesNotRenderAFabricatedInteractiveApplication() {
        val workspace = Workspace(
            id = "workspace-2",
            name = "<script>alert(1)</script>",
            description = "Framework workspace",
            type = WorkspaceType.WEB_APP
        )
        val html = WorkspaceSandboxEngine.generatePreviewHtml(workspace, emptyList())
        assertTrue(html.contains("Preview unavailable"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("Interactive Action"))
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("Content-Security-Policy"))
    }
}
