package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.util.AppBlockHelper
import com.example.util.FocusTimerManager

class AppBlockAccessibilityService : AccessibilityService() {

    private var lastDirectChatTime: Long = 0L
    private var isViewingSharedReelAllowed = false
    private var initialReelText: String? = null
    private var lastFeedScrollTime: Long = 0L
    private var scrollCountOnFeed = 0
    private var lastYtWatchDetectedTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // 1. Check general app blocking first
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AppBlockHelper.checkForegroundAppAndBlockIfNeeded(applicationContext, packageName)
        }

        // 2. Fine-grained Instagram blocking logic
        if (packageName == "com.instagram.android" && AppBlockHelper.isAppInBlockList(applicationContext, "com.instagram.android")) {
            handleInstagramBlocking(event)
        }

        // 3. Fine-grained YouTube blocking logic
        if (packageName == "com.google.android.youtube" && AppBlockHelper.isAppInBlockList(applicationContext, "com.google.android.youtube")) {
            handleYoutubeBlocking(event)
        }

        // 4. Fine-grained Snapchat blocking logic
        if (packageName == "com.snapchat.android" && AppBlockHelper.isAppInBlockList(applicationContext, "com.snapchat.android")) {
            handleSnapchatBlocking(event)
        }

        // 5. Fine-grained Facebook blocking logic
        if ((packageName == "com.facebook.katana" || packageName == "com.facebook.lite") &&
            (AppBlockHelper.isAppInBlockList(applicationContext, "com.facebook.katana") || AppBlockHelper.isAppInBlockList(applicationContext, "com.facebook.lite"))) {
            handleFacebookBlocking(event)
        }
    }

    private fun handleInstagramBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
        
        // Only enforce limits when Focus mode is active
        if (!isFocusing) {
            isViewingSharedReelAllowed = false
            initialReelText = null
            return
        }

        val useSelective = AppBlockHelper.isIgSelectiveBlockingEnabled(context)
        if (!useSelective) return

        val rootNode = rootInActiveWindow ?: return

        // Check if we are currently in Direct Messages / Chats
        val inDirectChat = checkInDirectChat(rootNode)
        if (inDirectChat) {
            lastDirectChatTime = System.currentTimeMillis()
            isViewingSharedReelAllowed = AppBlockHelper.isIgAllowSharedReels(context)
            initialReelText = null // Reset so we capture it when we enter Reels
            Log.d("InstagramBlocker", "User is in Direct Chat. Tracking for potential shared reel watch.")
        }

        // Check for Stories screen
        if (AppBlockHelper.isIgStoriesBlocked(context) && checkIsViewingStory(rootNode)) {
            Log.w("InstagramBlocker", "Stories blocked! Redirecting...")
            showToast("Instagram Stories are hidden during Focus phase! ☕")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check for Explore screen
        if (AppBlockHelper.isIgExploreBlocked(context) && checkIsViewingExplore(rootNode)) {
            Log.w("InstagramBlocker", "Explore blocked! Redirecting...")
            showToast("Instagram Explore is blocked to prevent doom scrolling! 🎯")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check for Reels screen
        if (AppBlockHelper.isIgReelsBlocked(context)) {
            val reelsResult = checkIsViewingReels(rootNode)
            if (reelsResult.isReels) {
                // If it's a Reels screen, check if it's allowed (shared reel from DMs)
                val timeSinceDirectChat = System.currentTimeMillis() - lastDirectChatTime
                val isRecentDm = timeSinceDirectChat < 8000 // clicked in the last 8 seconds
                
                if (isViewingSharedReelAllowed && isRecentDm) {
                    // Allowed to watch ONLY this single Reel.
                    // Capture the initial Reel identifier (text/caption/username) to detect scrolling.
                    val currentReelText = reelsResult.identifier ?: ""
                    if (initialReelText == null && currentReelText.isNotEmpty()) {
                        initialReelText = currentReelText
                        Log.d("InstagramBlocker", "Capturing initial shared reel ID: $initialReelText")
                    }

                    // If they scroll or if the text changes, it means they are trying to doomscroll to other reels!
                    val hasScrolled = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                    val idChanged = initialReelText != null && currentReelText.isNotEmpty() && currentReelText != initialReelText
                    
                    if (hasScrolled || idChanged) {
                        Log.w("InstagramBlocker", "Reel scrolled or changed! Back to chat. initial=$initialReelText, current=$currentReelText")
                        showToast("Delayed scroll blocker: returning to chat! 💬")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        isViewingSharedReelAllowed = false
                        initialReelText = null
                    }
                } else {
                    // Reels blocked entirely!
                    Log.w("InstagramBlocker", "Reels blocked! Redirecting...")
                    showToast("Instagram Reels are blocked. Stay focused! 🎯")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return
            }
        }

        // Feed Scroll limiting
        if (AppBlockHelper.isIgFeedScrollLimit(context)) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && checkIsViewingFeed(rootNode)) {
                val now = System.currentTimeMillis()
                if (now - lastFeedScrollTime > 1500) { // Limit scroll events to once per 1.5 seconds
                    scrollCountOnFeed++
                    lastFeedScrollTime = now
                    Log.d("InstagramBlocker", "Feed scroll count: $scrollCountOnFeed / 5")
                    if (scrollCountOnFeed >= 5) {
                        showToast("Feed scroll quota exceeded! Closing Instagram... 🛑")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        scrollCountOnFeed = 0
                    }
                }
            }
        }
    }

    private fun checkInDirectChat(node: AccessibilityNodeInfo): Boolean {
        // Find text or class names representing Direct Message chats
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val txt = it.text?.toString() ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val id = it.viewIdResourceName ?: ""
            id.contains("direct") || id.contains("message") || id.contains("thread") ||
            txt.contains("Message...") || txt.contains("Active now") || desc.contains("Chat")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingStory(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            id.contains("reel_viewer") || id.contains("story_viewer") || 
            id.contains("viewer_container") || desc.contains("Story") || desc.contains("Stories")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingExplore(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("search_explore") || id.contains("explore_tab") || 
            desc.contains("Search and Explore") || txt.contains("Explore")
        }
        return list.isNotEmpty()
    }

    private data class ReelsCheckResult(val isReels: Boolean, val identifier: String?)

    private fun checkIsViewingReels(node: AccessibilityNodeInfo): ReelsCheckResult {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            id.contains("reels_video_container") || id.contains("reels_view_pager") ||
            id.contains("reels_viewer") || desc.contains("Reels") || desc.contains("Double tap to like")
        }
        
        if (list.isEmpty()) return ReelsCheckResult(false, null)

        // Try to find a unique identifier for the current Reel (like caption or uploader's name)
        val textList = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, textList) {
            val id = it.viewIdResourceName ?: ""
            id.contains("reel_caption") || id.contains("username") || id.contains("caption")
        }
        
        val identifier = textList.firstOrNull()?.text?.toString() ?: textList.firstOrNull()?.contentDescription?.toString()
        return ReelsCheckResult(true, identifier)
    }

    private fun checkIsViewingFeed(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            id.contains("feed") || id.contains("home") || id.contains("root_container")
        }
        return list.isNotEmpty()
    }

    private fun findNodesByCriteria(
        node: AccessibilityNodeInfo?,
        resultList: MutableList<AccessibilityNodeInfo>,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (node == null) return
        if (predicate(node)) {
            resultList.add(node)
        }
        for (i in 0 until node.childCount) {
            findNodesByCriteria(node.getChild(i), resultList, predicate)
        }
    }

    private var lastToastTime = 0L
    private fun showToast(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 3000) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            lastToastTime = now
        }
    }

    private fun handleYoutubeBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
        
        if (!isFocusing) return

        val useSelective = AppBlockHelper.isYtSelectiveBlockingEnabled(context)
        if (!useSelective) return

        val rootNode = rootInActiveWindow ?: return

        // Check YouTube Shorts
        if (AppBlockHelper.isYtShortsBlocked(context) && checkIsViewingShorts(rootNode)) {
            Log.w("YoutubeBlocker", "YouTube Shorts blocked! Redirecting...")
            showToast("YouTube Shorts are blocked during Focus phase! ☕")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check YouTube Search
        if (AppBlockHelper.isYtSearchBlocked(context) && checkIsViewingSearch(rootNode)) {
            Log.w("YoutubeBlocker", "YouTube Search blocked! Redirecting...")
            showToast("YouTube Search is disabled to prevent distraction! 🎯")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check YouTube Comments
        if (AppBlockHelper.isYtCommentsBlocked(context) && checkIsViewingComments(rootNode)) {
            Log.w("YoutubeBlocker", "YouTube Comments blocked! Redirecting...")
            showToast("YouTube Comments are hidden during Focus phase! 💬")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check Approved Whitelisted Channels Restriction
        if (AppBlockHelper.isYtOnlyAllowApprovedChannels(context)) {
            val isWatchScreen = checkIsViewingYoutubeWatch(rootNode)
            if (!isWatchScreen) {
                lastYtWatchDetectedTime = 0L
                return
            }

            val allowedChannels = AppBlockHelper.getYtApprovedChannels(context)
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            if (allowedChannels.isEmpty()) {
                if (lastYtWatchDetectedTime == 0L) {
                    lastYtWatchDetectedTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastYtWatchDetectedTime > 1500) {
                    Log.w("YoutubeBlocker", "No whitelisted channels defined. Blocking watch screen...")
                    showToast("Configure whitelisted YouTube channels in Settings! ☕")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    lastYtWatchDetectedTime = 0L
                }
                return
            }

            val detectedNames = findChannelNamesOnScreen(rootNode).map { it.lowercase() }
            var hasMatch = detectedNames.any { detected ->
                allowedChannels.any { allowed ->
                    detected.contains(allowed) || allowed.contains(detected)
                }
            }

            if (!hasMatch) {
                // Global text scan fallback
                val allScreenTexts = mutableListOf<AccessibilityNodeInfo>()
                findNodesByCriteria(rootNode, allScreenTexts) {
                    it.text != null && it.text.toString().trim().isNotEmpty()
                }
                hasMatch = allScreenTexts.any { node ->
                    val text = node.text.toString().lowercase()
                    allowedChannels.any { allowed ->
                        text.contains(allowed) || allowed.contains(text)
                    }
                }
            }

            if (hasMatch) {
                lastYtWatchDetectedTime = 0L
            } else {
                if (lastYtWatchDetectedTime == 0L) {
                    lastYtWatchDetectedTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastYtWatchDetectedTime > 1500) {
                    Log.w("YoutubeBlocker", "Unapproved YouTube channel playing! Redirecting...")
                    showToast("Only whitelisted YouTube channels are allowed during Focus! ☕")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    lastYtWatchDetectedTime = 0L
                }
            }
        } else {
            lastYtWatchDetectedTime = 0L
        }
    }

    private fun handleSnapchatBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
        
        if (!isFocusing) return

        val useSelective = AppBlockHelper.isSnapSelectiveBlockingEnabled(context)
        if (!useSelective) return

        val rootNode = rootInActiveWindow ?: return

        // Check Spotlight
        if (AppBlockHelper.isSnapSpotlightBlocked(context) && checkIsViewingSnapchatSpotlight(rootNode)) {
            Log.w("SnapchatBlocker", "Snapchat Spotlight blocked! Redirecting...")
            showToast("Snapchat Spotlight is blocked during Focus phase! 🛑")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check Map
        if (AppBlockHelper.isSnapMapBlocked(context) && checkIsViewingSnapchatMap(rootNode)) {
            Log.w("SnapchatBlocker", "Snapchat Map blocked! Redirecting...")
            showToast("Snapchat Map is disabled during Focus phase! 🗺️")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check Discover / Stories
        if (AppBlockHelper.isSnapDiscoverBlocked(context) && checkIsViewingSnapchatDiscover(rootNode)) {
            Log.w("SnapchatBlocker", "Snapchat Discover/Stories blocked! Redirecting...")
            showToast("Snapchat Discover & Stories are hidden to keep you focused! ☕")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
    }

    private fun handleFacebookBlocking(event: AccessibilityEvent) {
        val context = applicationContext
        val isFocusing = (FocusTimerManager.isTimerRunning.value || FocusTimerManager.isStopwatchActive.value) && FocusTimerManager.isFocusPhase.value
        
        if (!isFocusing) return

        val useSelective = AppBlockHelper.isFbSelectiveBlockingEnabled(context)
        if (!useSelective) return

        val rootNode = rootInActiveWindow ?: return

        // Check Facebook Reels
        if (AppBlockHelper.isFbReelsBlocked(context) && checkIsViewingFbReels(rootNode)) {
            Log.w("FacebookBlocker", "Facebook Reels blocked! Redirecting...")
            showToast("Facebook Reels are blocked during Focus phase! ☕")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check Facebook Watch / Video
        if (AppBlockHelper.isFbWatchBlocked(context) && checkIsViewingFbWatch(rootNode)) {
            Log.w("FacebookBlocker", "Facebook Watch/Video blocked! Redirecting...")
            showToast("Facebook Watch/Video is disabled to keep you focused! 🎯")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check Facebook Stories
        if (AppBlockHelper.isFbStoriesBlocked(context) && checkIsViewingFbStories(rootNode)) {
            Log.w("FacebookBlocker", "Facebook Stories blocked! Redirecting...")
            showToast("Facebook Stories are hidden to keep you focused! 💬")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
    }

    private fun checkIsViewingShorts(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("shorts") || id.contains("Shorts") || id.contains("reel_container") ||
            desc.contains("Shorts") || desc.contains("shorts") || txt.contains("Shorts") || txt.contains("shorts")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingSearch(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("search_box") || id.contains("search_edit_text") || id.contains("search_btn") || txt.contains("Search")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingComments(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("comment") || id.contains("comments") || txt.contains("Comments") || txt.contains("Add a comment")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingYoutubeWatch(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            id.contains("watch_next") || id.contains("watch_panel") || id.contains("player_fragment_container") || 
            id.contains("subscribe_button") || id.contains("video_info_fragment") || id.contains("watch_focus_layout")
        }
        return list.isNotEmpty()
    }

    private fun findChannelNamesOnScreen(node: AccessibilityNodeInfo): List<String> {
        val names = mutableListOf<String>()
        val list = mutableListOf<AccessibilityNodeInfo>()
        
        // Find owner/channel/author nodes
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            id.contains("owner_name") || id.contains("channel_name") || id.contains("owner_title") || 
            id.contains("channel_title") || id.contains("author_text") || id.contains("owner_text") || 
            id.contains("uploader_name") || id.contains("channel_title_view") || id.contains("owner_header")
        }
        for (n in list) {
            n.text?.toString()?.trim()?.let { if (it.isNotEmpty()) names.add(it) }
        }
        
        // Also look near the subscribe button
        val subscribeList = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, subscribeList) {
            val id = it.viewIdResourceName ?: ""
            id.contains("subscribe_button") || id.contains("subscribe")
        }
        for (subNode in subscribeList) {
            var parent = subNode.parent
            for (level in 0..1) {
                if (parent != null) {
                    val childTexts = mutableListOf<AccessibilityNodeInfo>()
                    findNodesByCriteria(parent, childTexts) {
                        it.text != null && it.text.toString().trim().isNotEmpty()
                    }
                    for (ct in childTexts) {
                        ct.text?.toString()?.trim()?.let { 
                            val lower = it.lowercase()
                            if (!lower.contains("subscrib") && !lower.matches(Regex("\\d+.*")) && it.length > 2) {
                                names.add(it)
                            }
                        }
                    }
                    parent = parent.parent
                }
            }
        }
        
        return names.distinct()
    }

    private fun checkIsViewingSnapchatSpotlight(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("spotlight") || id.contains("Spotlight") ||
            desc.contains("Spotlight") || desc.contains("spotlight") ||
            txt.contains("Spotlight") || txt.contains("spotlight")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingSnapchatMap(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("snap_map") || id.contains("map_view") || id.contains("map") ||
            desc.contains("Map") || desc.contains("map") || desc.contains("Location") ||
            txt.contains("Map") || txt.contains("map")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingSnapchatDiscover(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("discover") || id.contains("Discover") || id.contains("stories") || id.contains("Stories") ||
            desc.contains("Discover") || desc.contains("discover") || desc.contains("Stories") || desc.contains("stories") ||
            txt.contains("Discover") || txt.contains("discover") || txt.contains("Stories") || txt.contains("stories") ||
            id.contains("sub_feed") || id.contains("news_feed")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingFbReels(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("reel") || id.contains("Reel") || id.contains("short_form") ||
            desc.contains("Reel") || desc.contains("reel") || desc.contains("Short video") ||
            txt.contains("Reel") || txt.contains("reel") || txt.contains("Short video")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingFbWatch(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("watch") || id.contains("Watch") || id.contains("fb_watch") || id.contains("video_tab") ||
            desc.contains("Watch") || desc.contains("watch") || desc.contains("Video") || desc.contains("video") ||
            txt.contains("Watch") || txt.contains("fb_watch") || txt.contains("video")
        }
        return list.isNotEmpty()
    }

    private fun checkIsViewingFbStories(node: AccessibilityNodeInfo): Boolean {
        val list = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(node, list) {
            val id = it.viewIdResourceName ?: ""
            val desc = it.contentDescription?.toString() ?: ""
            val txt = it.text?.toString() ?: ""
            id.contains("stories") || id.contains("Stories") || id.contains("story_tray") || id.contains("stories_tray") ||
            desc.contains("Story") || desc.contains("Stories") || desc.contains("story") || desc.contains("stories") ||
            txt.contains("Story") || txt.contains("Stories") || txt.contains("story") || txt.contains("stories")
        }
        return list.isNotEmpty()
    }

    override fun onInterrupt() {
        Log.d("AppBlockAccessibility", "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AppBlockAccessibility", "Accessibility service connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or 
                         AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        this.serviceInfo = info
    }
}
