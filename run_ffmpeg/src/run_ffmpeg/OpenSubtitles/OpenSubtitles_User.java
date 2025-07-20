package run_ffmpeg.OpenSubtitles;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenSubtitles_User
{
	private Integer allowedDownloads;
	private Integer allowedTranslations;
	private String level;
	private Integer userId;
	private Boolean extInstalled;
	private Boolean vip;
	private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

	public Integer getAllowedDownloads() {
		return allowedDownloads;
	}

	public void setAllowedDownloads(Integer allowedDownloads) {
		this.allowedDownloads = allowedDownloads;
	}

	public Integer getAllowedTranslations() {
		return allowedTranslations;
	}

	public void setAllowedTranslations(Integer allowedTranslations) {
		this.allowedTranslations = allowedTranslations;
	}

	public String getLevel() {
		return level;
	}

	public void setLevel(String level) {
		this.level = level;
	}

	public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	public Boolean getExtInstalled() {
		return extInstalled;
	}

	public void setExtInstalled(Boolean extInstalled) {
		this.extInstalled = extInstalled;
	}

	public Boolean getVip() {
		return vip;
	}

	public void setVip(Boolean vip) {
		this.vip = vip;
	}

	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(OpenSubtitles_LoginResponse.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
		sb.append("allowedDownloads");
		sb.append('=');
		sb.append(((this.allowedDownloads == null)?"<null>":this.allowedDownloads));
		sb.append(',');
		sb.append("allowedTranslations");
		sb.append('=');
		sb.append(((this.allowedTranslations == null)?"<null>":this.allowedTranslations));
		sb.append(',');
		sb.append("level");
		sb.append('=');
		sb.append(((this.level == null)?"<null>":this.level));
		sb.append(',');
		sb.append("userId");
		sb.append('=');
		sb.append(((this.userId == null)?"<null>":this.userId));
		sb.append(',');
		sb.append("extInstalled");
		sb.append('=');
		sb.append(((this.extInstalled == null)?"<null>":this.extInstalled));
		sb.append(',');
		sb.append("vip");
		sb.append('=');
		sb.append(((this.vip == null)?"<null>":this.vip));
		sb.append(',');
		sb.append("additionalProperties");
		sb.append('=');
		sb.append(((this.additionalProperties == null)?"<null>":this.additionalProperties));
		sb.append(',');
		if (sb.charAt((sb.length()- 1)) == ',') {
			sb.setCharAt((sb.length()- 1), ']');
		} else {
			sb.append(']');
		}
		return sb.toString();
	}
}
