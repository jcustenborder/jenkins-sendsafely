package com.github.jcustenborder.jenkins.sendsafely;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.sendsafely.File;
import com.sendsafely.Package;
import com.sendsafely.SendSafely;
import com.sendsafely.dto.PackageURL;
import com.sendsafely.exceptions.ApproverRequiredException;
import com.sendsafely.exceptions.CreatePackageFailedException;
import com.sendsafely.exceptions.FinalizePackageFailedException;
import com.sendsafely.exceptions.LimitExceededException;
import com.sendsafely.exceptions.MessageException;
import com.sendsafely.exceptions.RecipientFailedException;
import com.sendsafely.exceptions.UpdatePackageLifeException;
import com.sendsafely.exceptions.UploadFileException;
import com.sendsafely.file.FileManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

public class SendSafelyBuilder extends Builder implements SimpleBuildStep {
  private String credentialID;
  private String includes;
  private Integer packageLife;
  private String excludes;
  private String sendSafelyHost = "https://app.sendsafely.com";
  private boolean notify = true;
  private String recipients;
  private String message;

  @DataBoundConstructor
  public SendSafelyBuilder() {

  }

  @DataBoundSetter
  public void setCredentialID(String credentialID) {
    this.credentialID = credentialID;
  }

  @DataBoundSetter
  public void setIncludes(String includes) {
    this.includes = includes;
  }

  @DataBoundSetter
  public void setPackageLife(Integer packageLife) {
    this.packageLife = packageLife;
  }

  @DataBoundSetter
  public void setExcludes(String excludes) {
    this.excludes = excludes;
  }

  @DataBoundSetter
  public void setSendSafelyHost(String sendSafelyHost) {
    this.sendSafelyHost = sendSafelyHost;
  }

  @DataBoundSetter
  public void setNotify(boolean notify) {
    this.notify = notify;
  }

  @DataBoundSetter
  public void setRecipients(String recipients) {
    this.recipients = recipients;
  }

  @DataBoundSetter
  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
    listener.getLogger().println("Searching for files to upload.");
    FilePath[] filesToUpload = workspace.list(this.includes, this.excludes);

    if (filesToUpload.length == 0) {
      listener.getLogger().println("No files were found.\n");
      run.setResult(Result.FAILURE);
      return;
    } else {
      listener.getLogger().format("Found %s file(s) to upload.\n", filesToUpload.length);
    }

    StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(this.credentialID, StandardUsernamePasswordCredentials.class, run);

    SendSafely sendSafely = new SendSafely(
        this.sendSafelyHost,
        credentials.getUsername(),
        credentials.getPassword().getPlainText()
    );

    try {
      Package sendSafelyPackage = sendSafely.createPackage();

      for (FilePath fileToUpload : filesToUpload) {
        FileManager fileManager = new FilePathFileManager(fileToUpload);
        File uploadedFile = sendSafely.encryptAndUploadFile(
            sendSafelyPackage.getPackageId(),
            sendSafelyPackage.getKeyCode(),
            fileManager
        );
        listener.getLogger().printf("Added %s(%s) to %s.\n", uploadedFile.getFileName(), uploadedFile.getFileId(), sendSafelyPackage.getPackageId());
      }

      if (null != this.message && !this.message.isEmpty()) {
        sendSafely.encryptAndUploadMessage(sendSafelyPackage.getPackageId(), sendSafelyPackage.getKeyCode(), this.message);
      }

      if (null != this.packageLife) {
        listener.getLogger().printf("Updating package life for %s to %s.\n", sendSafelyPackage.getPackageId(), this.packageLife);
        sendSafely.updatePackageLife(sendSafelyPackage.getPackageId(), this.packageLife);
      }

      if (null != this.recipients && !this.recipients.isEmpty()) {
        String[] split = this.recipients.split("\\s*,\\s*");
        for (String recipient : split) {
          sendSafely.addRecipient(sendSafelyPackage.getPackageId(), recipient);
        }
      }


      PackageURL packageUrl = sendSafely.finalizePackage(
          sendSafelyPackage.getPackageId(),
          sendSafelyPackage.getKeyCode(),
          this.notify
      );
      run.setResult(Result.SUCCESS);
    } catch (CreatePackageFailedException | FinalizePackageFailedException | ApproverRequiredException | UploadFileException | LimitExceededException | UpdatePackageLifeException | RecipientFailedException | MessageException e) {
      listener.getLogger().println(e.getMessage());
      run.setResult(Result.FAILURE);
    }
  }


  @Symbol("sendsafely")
  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Send Safely";
    }

  }

}
