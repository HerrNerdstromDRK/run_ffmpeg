package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_User
{
	private Integer allowed_downloads;
	private Integer allowed_translations;
	private String level;
	private Integer user_id;
	private Boolean ext_installed;
	private Boolean vip;

	public String getLevel() {
		return level;
	}

	public void setLevel(String level) {
		this.level = level;
	}

	public Boolean getVip() {
		return vip;
	}

	public void setVip(Boolean vip) {
		this.vip = vip;
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

	public Integer getAllowed_downloads() {
		return allowed_downloads;
	}

	public void setAllowed_downloads(Integer allowed_downloads) {
		this.allowed_downloads = allowed_downloads;
	}

	public Integer getAllowed_translations() {
		return allowed_translations;
	}

	public void setAllowed_translations(Integer allowed_translations) {
		this.allowed_translations = allowed_translations;
	}

	public Integer getUser_id() {
		return user_id;
	}

	public void setUser_id(Integer user_id) {
		this.user_id = user_id;
	}

	public Boolean getExt_installed() {
		return ext_installed;
	}

	public void setExt_installed(Boolean ext_installed) {
		this.ext_installed = ext_installed;
	}
}
