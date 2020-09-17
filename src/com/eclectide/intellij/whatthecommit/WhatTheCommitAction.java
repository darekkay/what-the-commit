package com.eclectide.intellij.whatthecommit;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WhatTheCommitAction extends AnAction implements DumbAware {

    private static final String URL = "http://whatthecommit.com/index.txt";
    private static final int TIMEOUT_SECONDS = 5;

    public void actionPerformed(@NotNull AnActionEvent e) {
        final CommitMessageI checkinPanel = getCheckinPanel(e);
        if (checkinPanel == null)
            return;

        String commitMessage = loadCommitMessage(URL);
        if (!commitMessage.isEmpty()) {
            checkinPanel.setCommitMessage(commitMessage);
        }
    }

    private String readUrlContent(String urlString) {
        HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();

        HttpURLConnection connection = null;

        try {
            connection = httpConfigurable.openHttpConnection(urlString);

            String text = StreamUtil.readText(connection.getInputStream(), "UTF-8");
            return text.trim();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public String loadCommitMessage(final String url) {
        final FutureTask<String> downloadTask = new FutureTask<String>(new Callable<String>() {
            public String call() {
				try {
				  final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
				  httpConfigurable.prepareURL(url);
				} catch (IOException ioe) {
				  IOExceptionDialog.showErrorDialog("Error", String.format("Unable to connect to \"%s\". Make sure your proxy settings are correct.", url));
				  return null;
				}

				return readUrlContent(url);
            }
        });

        ApplicationManager.getApplication().executeOnPooledThread(downloadTask);

        try {
            return downloadTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // ignore
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (!downloadTask.isDone()) {
            downloadTask.cancel(true);
            throw new RuntimeException("Connection timed out");
        }

        return "";
    }

    @Nullable
    private static CommitMessageI getCheckinPanel(@Nullable AnActionEvent e) {
        if (e == null) {
            return null;
        }
        Refreshable data = Refreshable.PANEL_KEY.getData(e.getDataContext());
        if (data instanceof CommitMessageI) {
            return (CommitMessageI) data;
        }
        return VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(e.getDataContext());
    }
}
