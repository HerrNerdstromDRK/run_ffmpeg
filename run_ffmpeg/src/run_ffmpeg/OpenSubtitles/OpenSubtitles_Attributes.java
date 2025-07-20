package run_ffmpeg.OpenSubtitles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.json.bind.annotation.JsonbProperty;

public class OpenSubtitles_Attributes
{
	private String subtitle_id;
	private String language;
	private Integer download_count;
	private Integer new_download_count;
	private Boolean hearing_impaired;
	private Boolean hd;
	private Double fps;
	private Integer votes;
	private Double ratings;
	private Boolean from_trusted;
	private Boolean foreign_parts_only;
	private String upload_date;
	private Boolean ai_translated;
	private Integer nb_cd;
	private String slug;
	private Boolean machine_translated;
	private String release;
	private String comments;
	private Integer legacy_subtitle_id;
	private Integer legacy_uploader_id;
	private OpenSubtitles_Uploader uploader;
	private OpenSubtitles_FeatureDetails feature_details;
	private String url;
	private List<OpenSubtitles_RelatedLink> related_links;
	private List<OpenSubtitles_File> files;

	private transient Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

	@JsonbProperty("language")
	public String getLanguage() {
		return language;
	}

	@JsonbProperty("language")
	public void setLanguage(String language) {
		this.language = language;
	}

	@JsonbProperty("hd")
	public Boolean getHd() {
		return hd;
	}

	@JsonbProperty("hd")
	public void setHd(Boolean hd) {
		this.hd = hd;
	}

	@JsonbProperty("fps")
	public Double getFps() {
		return fps;
	}

	@JsonbProperty("fps")
	public void setFps(Double fps) {
		this.fps = fps;
	}

	@JsonbProperty("votes")
	public Integer getVotes() {
		return votes;
	}

	@JsonbProperty("votes")
	public void setVotes(Integer votes) {
		this.votes = votes;
	}

	@JsonbProperty("ratings")
	public Double getRatings() {
		return ratings;
	}

	@JsonbProperty("ratings")
	public void setRatings(Double ratings) {
		this.ratings = ratings;
	}

	@JsonbProperty("slug")
	public String getSlug() {
		return slug;
	}

	@JsonbProperty("slug")
	public void setSlug(String slug) {
		this.slug = slug;
	}

	@JsonbProperty("release")
	public String getRelease() {
		return release;
	}

	@JsonbProperty("release")
	public void setRelease(String release) {
		this.release = release;
	}

	@JsonbProperty("comments")
	public String getComments() {
		return comments;
	}

	@JsonbProperty("comments")
	public void setComments(String comments) {
		this.comments = comments;
	}

	@JsonbProperty("uploader")
	public OpenSubtitles_Uploader getUploader() {
		return uploader;
	}

	@JsonbProperty("uploader")
	public void setUploader(OpenSubtitles_Uploader uploader) {
		this.uploader = uploader;
	}

	@JsonbProperty("url")
	public String getUrl() {
		return url;
	}

	@JsonbProperty("url")
	public void setUrl(String url) {
		this.url = url;
	}

	@JsonbProperty("files")
	public List<OpenSubtitles_File> getFiles() {
		return files;
	}

	@JsonbProperty("files")
	public void setFiles(List<OpenSubtitles_File> files) {
		this.files = files;
	}

	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	@Override
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder() ; 
		builder.setPrettyPrinting() ; 
		Gson gson = builder.create() ;
		final String json = gson.toJson( this ) ;
		return json ;
	}

	public String getSubtitle_id() {
		return subtitle_id;
	}

	public void setSubtitle_id(String subtitle_id) {
		this.subtitle_id = subtitle_id;
	}

	public Integer getDownload_count() {
		return download_count;
	}

	public void setDownload_count(Integer download_count) {
		this.download_count = download_count;
	}

	public Integer getNew_download_count() {
		return new_download_count;
	}

	public void setNew_download_count(Integer new_download_count) {
		this.new_download_count = new_download_count;
	}

	public Boolean getHearing_impaired() {
		return hearing_impaired;
	}

	public void setHearing_impaired(Boolean hearing_impaired) {
		this.hearing_impaired = hearing_impaired;
	}

	public Boolean getFrom_trusted() {
		return from_trusted;
	}

	public void setFrom_trusted(Boolean from_trusted) {
		this.from_trusted = from_trusted;
	}

	public Boolean getForeign_parts_only() {
		return foreign_parts_only;
	}

	public void setForeign_parts_only(Boolean foreign_parts_only) {
		this.foreign_parts_only = foreign_parts_only;
	}

	public String getUpload_date() {
		return upload_date;
	}

	public void setUpload_date(String upload_date) {
		this.upload_date = upload_date;
	}

	public Boolean getAi_translated() {
		return ai_translated;
	}

	public void setAi_translated(Boolean ai_translated) {
		this.ai_translated = ai_translated;
	}

	public Integer getNb_cd() {
		return nb_cd;
	}

	public void setNb_cd(Integer nb_cd) {
		this.nb_cd = nb_cd;
	}

	public Boolean getMachine_translated() {
		return machine_translated;
	}

	public void setMachine_translated(Boolean machine_translated) {
		this.machine_translated = machine_translated;
	}

	public Integer getLegacy_subtitle_id() {
		return legacy_subtitle_id;
	}

	public void setLegacy_subtitle_id(Integer legacy_subtitle_id) {
		this.legacy_subtitle_id = legacy_subtitle_id;
	}

	public Integer getLegacy_uploader_id() {
		return legacy_uploader_id;
	}

	public void setLegacy_uploader_id(Integer legacy_uploader_id) {
		this.legacy_uploader_id = legacy_uploader_id;
	}

	public OpenSubtitles_FeatureDetails getFeature_details() {
		return feature_details;
	}

	public void setFeature_details(OpenSubtitles_FeatureDetails feature_details) {
		this.feature_details = feature_details;
	}

	public List<OpenSubtitles_RelatedLink> getRelated_links() {
		return related_links;
	}

	public void setRelated_links(List<OpenSubtitles_RelatedLink> related_links) {
		this.related_links = related_links;
	}

	public void setAdditionalProperties(Map<String, Object> additionalProperties) {
		this.additionalProperties = additionalProperties;
	}
}