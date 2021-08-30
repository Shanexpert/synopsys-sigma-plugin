/*
 * Copyright (c) 2021 Synopsys, Inc. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.sigma.extension.tool;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.sigma.Messages;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;

public class SigmaBinaryInstaller extends ToolInstaller {
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private String downloadUrl;
    private int timeout;

    public SigmaBinaryInstaller(String label) {
        super(label);
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    @DataBoundSetter
    public void setDownloadUrl(final String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public int getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(final int timeout) {
        this.timeout = timeout;
    }

    @Override
    public SigmaBinaryInstaller.DescriptorImpl getDescriptor() {
        return (SigmaBinaryInstaller.DescriptorImpl) super.getDescriptor();
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws AbortException {
        FilePath installLocation = preferredLocation(tool, node);
        String errorMessage = String.format("Failed to install Sigma on Node %s from %s.", node.getDisplayName(), downloadUrl);
        try {
            final VirtualChannel virtualChannel = node.getChannel();
            if (virtualChannel == null) {
                throw new AbortException("Configured node \"" + node.getDisplayName() + "\" is either not connected or offline.  Cannot install Sigma.");
            }
            // timeout is in seconds convert to milliseconds.
            int timeoutInMilliseconds = timeout * 1000;
            BinaryUpdateCheck updateChecker = new BinaryUpdateCheck(downloadUrl, timeoutInMilliseconds);
            virtualChannel.call(new FileDownloadInstaller(downloadUrl, installLocation, timeoutInMilliseconds, log, updateChecker));
        } catch (InterruptedException ex) {
            ex.printStackTrace(log.error(errorMessage));
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            ex.printStackTrace(log.error(errorMessage));
            throw new AbortException(errorMessage);
        }
        return installLocation;
    }

    @Extension
    public static final class DescriptorImpl extends ToolInstallerDescriptor<SigmaBinaryInstaller> {
        public String getDisplayName() {
            return Messages.installer_displayName();
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == SigmaToolInstallation.class;
        }

        public FormValidation doCheckDownloadUrl(@QueryParameter String value) throws IOException, ServletException {
            try {
                if (StringUtils.isBlank(value)) {
                    return FormValidation.error(Messages.installer_error_downloadurl_empty());
                }
                new URL(value);
            } catch (MalformedURLException ex) {
                return FormValidation.error(ex, Messages.installer_error_downloadurl_malformed());
            }
            return FormValidation.ok();
        }

    }
}
