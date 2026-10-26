package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * Shopper&#39;s browser data, used for EMV 3DS 2.x when N-Genius asks for 3DS.
 */

public class DeviceInfo {

  private String ip;

  private String userAgent;

  private String acceptHeader;

  private String language;

  private @Nullable String timeZoneOffset;

  private @Nullable String screenWidth;

  private @Nullable String screenHeight;

  private @Nullable String colorDepth;

  private @Nullable Boolean javascriptEnabled;

  private @Nullable Boolean javaEnabled;

  private @Nullable String challengeWindowSize;

  public DeviceInfo() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public DeviceInfo(String ip, String userAgent, String acceptHeader, String language) {
    this.ip = ip;
    this.userAgent = userAgent;
    this.acceptHeader = acceptHeader;
    this.language = language;
  }

  public DeviceInfo ip(String ip) {
    this.ip = ip;
    return this;
  }

  /**
   * Get ip
   * @return ip
   */
  @NotNull @Size(min = 1, max = 45) 
  @JsonProperty("ip")
  public String getIp() {
    return ip;
  }

  @JsonProperty("ip")
  public void setIp(String ip) {
    this.ip = ip;
  }

  public DeviceInfo userAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  /**
   * Get userAgent
   * @return userAgent
   */
  @NotNull @Size(min = 1, max = 2048) 
  @JsonProperty("userAgent")
  public String getUserAgent() {
    return userAgent;
  }

  @JsonProperty("userAgent")
  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public DeviceInfo acceptHeader(String acceptHeader) {
    this.acceptHeader = acceptHeader;
    return this;
  }

  /**
   * Get acceptHeader
   * @return acceptHeader
   */
  @NotNull @Size(min = 1, max = 2048) 
  @JsonProperty("acceptHeader")
  public String getAcceptHeader() {
    return acceptHeader;
  }

  @JsonProperty("acceptHeader")
  public void setAcceptHeader(String acceptHeader) {
    this.acceptHeader = acceptHeader;
  }

  public DeviceInfo language(String language) {
    this.language = language;
    return this;
  }

  /**
   * Get language
   * @return language
   */
  @NotNull @Size(min = 1, max = 8) 
  @JsonProperty("language")
  public String getLanguage() {
    return language;
  }

  @JsonProperty("language")
  public void setLanguage(String language) {
    this.language = language;
  }

  public DeviceInfo timeZoneOffset(@Nullable String timeZoneOffset) {
    this.timeZoneOffset = timeZoneOffset;
    return this;
  }

  /**
   * new Date().getTimezoneOffset() (UTC+4 = -240)
   * @return timeZoneOffset
   */
  @Pattern(regexp = "^-?\\d{1,4}$") 
  @JsonProperty("timeZoneOffset")
  public @Nullable String getTimeZoneOffset() {
    return timeZoneOffset;
  }

  @JsonProperty("timeZoneOffset")
  public void setTimeZoneOffset(@Nullable String timeZoneOffset) {
    this.timeZoneOffset = timeZoneOffset;
  }

  public DeviceInfo screenWidth(@Nullable String screenWidth) {
    this.screenWidth = screenWidth;
    return this;
  }

  /**
   * Get screenWidth
   * @return screenWidth
   */
  @Pattern(regexp = "^\\d{1,6}$") 
  @JsonProperty("screenWidth")
  public @Nullable String getScreenWidth() {
    return screenWidth;
  }

  @JsonProperty("screenWidth")
  public void setScreenWidth(@Nullable String screenWidth) {
    this.screenWidth = screenWidth;
  }

  public DeviceInfo screenHeight(@Nullable String screenHeight) {
    this.screenHeight = screenHeight;
    return this;
  }

  /**
   * Get screenHeight
   * @return screenHeight
   */
  @Pattern(regexp = "^\\d{1,6}$") 
  @JsonProperty("screenHeight")
  public @Nullable String getScreenHeight() {
    return screenHeight;
  }

  @JsonProperty("screenHeight")
  public void setScreenHeight(@Nullable String screenHeight) {
    this.screenHeight = screenHeight;
  }

  public DeviceInfo colorDepth(@Nullable String colorDepth) {
    this.colorDepth = colorDepth;
    return this;
  }

  /**
   * Get colorDepth
   * @return colorDepth
   */
  @Pattern(regexp = "^\\d{1,2}$") 
  @JsonProperty("colorDepth")
  public @Nullable String getColorDepth() {
    return colorDepth;
  }

  @JsonProperty("colorDepth")
  public void setColorDepth(@Nullable String colorDepth) {
    this.colorDepth = colorDepth;
  }

  public DeviceInfo javascriptEnabled(@Nullable Boolean javascriptEnabled) {
    this.javascriptEnabled = javascriptEnabled;
    return this;
  }

  /**
   * Get javascriptEnabled
   * @return javascriptEnabled
   */
  
  @JsonProperty("javascriptEnabled")
  public @Nullable Boolean getJavascriptEnabled() {
    return javascriptEnabled;
  }

  @JsonProperty("javascriptEnabled")
  public void setJavascriptEnabled(@Nullable Boolean javascriptEnabled) {
    this.javascriptEnabled = javascriptEnabled;
  }

  public DeviceInfo javaEnabled(@Nullable Boolean javaEnabled) {
    this.javaEnabled = javaEnabled;
    return this;
  }

  /**
   * Get javaEnabled
   * @return javaEnabled
   */
  
  @JsonProperty("javaEnabled")
  public @Nullable Boolean getJavaEnabled() {
    return javaEnabled;
  }

  @JsonProperty("javaEnabled")
  public void setJavaEnabled(@Nullable Boolean javaEnabled) {
    this.javaEnabled = javaEnabled;
  }

  public DeviceInfo challengeWindowSize(@Nullable String challengeWindowSize) {
    this.challengeWindowSize = challengeWindowSize;
    return this;
  }

  /**
   * 01=250x400, 02=390x400, 03=500x600, 04=600x400, 05=full screen (default)
   * @return challengeWindowSize
   */
  @Pattern(regexp = "^0[1-5]$") 
  @JsonProperty("challengeWindowSize")
  public @Nullable String getChallengeWindowSize() {
    return challengeWindowSize;
  }

  @JsonProperty("challengeWindowSize")
  public void setChallengeWindowSize(@Nullable String challengeWindowSize) {
    this.challengeWindowSize = challengeWindowSize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceInfo deviceInfo = (DeviceInfo) o;
    return Objects.equals(this.ip, deviceInfo.ip) &&
        Objects.equals(this.userAgent, deviceInfo.userAgent) &&
        Objects.equals(this.acceptHeader, deviceInfo.acceptHeader) &&
        Objects.equals(this.language, deviceInfo.language) &&
        Objects.equals(this.timeZoneOffset, deviceInfo.timeZoneOffset) &&
        Objects.equals(this.screenWidth, deviceInfo.screenWidth) &&
        Objects.equals(this.screenHeight, deviceInfo.screenHeight) &&
        Objects.equals(this.colorDepth, deviceInfo.colorDepth) &&
        Objects.equals(this.javascriptEnabled, deviceInfo.javascriptEnabled) &&
        Objects.equals(this.javaEnabled, deviceInfo.javaEnabled) &&
        Objects.equals(this.challengeWindowSize, deviceInfo.challengeWindowSize);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ip, userAgent, acceptHeader, language, timeZoneOffset, screenWidth, screenHeight, colorDepth, javascriptEnabled, javaEnabled, challengeWindowSize);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class DeviceInfo {\n");
    sb.append("    ip: ").append(toIndentedString(ip)).append("\n");
    sb.append("    userAgent: ").append(toIndentedString(userAgent)).append("\n");
    sb.append("    acceptHeader: ").append(toIndentedString(acceptHeader)).append("\n");
    sb.append("    language: ").append(toIndentedString(language)).append("\n");
    sb.append("    timeZoneOffset: ").append(toIndentedString(timeZoneOffset)).append("\n");
    sb.append("    screenWidth: ").append(toIndentedString(screenWidth)).append("\n");
    sb.append("    screenHeight: ").append(toIndentedString(screenHeight)).append("\n");
    sb.append("    colorDepth: ").append(toIndentedString(colorDepth)).append("\n");
    sb.append("    javascriptEnabled: ").append(toIndentedString(javascriptEnabled)).append("\n");
    sb.append("    javaEnabled: ").append(toIndentedString(javaEnabled)).append("\n");
    sb.append("    challengeWindowSize: ").append(toIndentedString(challengeWindowSize)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(@Nullable Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}

