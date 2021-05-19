package com.github.jcustenborder.jenkins.sendsafely;

import com.sendsafely.file.FileManager;
import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class FilePathFileManager implements FileManager {
  private final FilePath filePath;

  public FilePathFileManager(FilePath filePath) {
    this.filePath = filePath;
  }

  @Override
  public long length() throws IOException {
    try {
      return this.filePath.length();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public String getName() throws IOException {
    return this.filePath.getName();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    try {
      return this.filePath.read();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    try {
      return this.filePath.write();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public FileManager createTempFile(String s, String s1, long l) throws IOException {
    try {
      FilePath filePath = this.filePath.getParent().createTempFile(s, s1);
      return new FilePathFileManager(filePath);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void remove() throws IOException {
    try {
      this.filePath.delete();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }
}
