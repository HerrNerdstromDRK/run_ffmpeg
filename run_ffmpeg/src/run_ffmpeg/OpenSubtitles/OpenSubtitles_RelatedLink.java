package run_ffmpeg.OpenSubtitles;

import java.util.LinkedHashMap;
import java.util.Map;

public class OpenSubtitles_RelatedLink
{
	private String label;
	private String url;
	private String imgUrl;
	private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getImgUrl() {
		return imgUrl;
	}

	public void setImgUrl(String imgUrl) {
		this.imgUrl = imgUrl;
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
		sb.append(OpenSubtitles_RelatedLink.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
		sb.append("label");
		sb.append('=');
		sb.append(((this.label == null)?"<null>":this.label));
		sb.append(',');
		sb.append("url");
		sb.append('=');
		sb.append(((this.url == null)?"<null>":this.url));
		sb.append(',');
		sb.append("imgUrl");
		sb.append('=');
		sb.append(((this.imgUrl == null)?"<null>":this.imgUrl));
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
