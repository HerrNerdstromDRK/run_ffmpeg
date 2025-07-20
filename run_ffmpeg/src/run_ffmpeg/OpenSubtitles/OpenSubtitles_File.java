package run_ffmpeg.OpenSubtitles;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenSubtitles_File
{
	private Integer fileId;
	private Integer cdNumber;
	private String fileName;
	private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

	public Integer getFileId() {
		return fileId;
	}

	public void setFileId(Integer fileId) {
		this.fileId = fileId;
	}

	public Integer getCdNumber() {
		return cdNumber;
	}

	public void setCdNumber(Integer cdNumber) {
		this.cdNumber = cdNumber;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
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
		sb.append(OpenSubtitles_File.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
		sb.append("fileId");
		sb.append('=');
		sb.append(((this.fileId == null)?"<null>":this.fileId));
		sb.append(',');
		sb.append("cdNumber");
		sb.append('=');
		sb.append(((this.cdNumber == null)?"<null>":this.cdNumber));
		sb.append(',');
		sb.append("fileName");
		sb.append('=');
		sb.append(((this.fileName == null)?"<null>":this.fileName));
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