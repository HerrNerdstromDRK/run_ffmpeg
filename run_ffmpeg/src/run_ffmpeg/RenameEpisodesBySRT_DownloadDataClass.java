package run_ffmpeg;

import java.io.File;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import run_ffmpeg.OpenSubtitles.OpenSubtitles_Data;

public class RenameEpisodesBySRT_DownloadDataClass
{
	public String imdbShowIDString = null ;
	public int seasonNumber = 0 ;
	public int episodeNumber = 0 ;
	public OpenSubtitles_Data subtitleData = null ;
	public File outputDirectory = null ;

	public RenameEpisodesBySRT_DownloadDataClass(
			final String imdbShowIDString,
			final int seasonNumber,
			final int episodeNumber,
			final OpenSubtitles_Data subtitleData,
			final File outputDirectory )
	{
		super() ;
		this.imdbShowIDString = imdbShowIDString;
		this.seasonNumber = seasonNumber;
		this.episodeNumber = episodeNumber;
		this.subtitleData = subtitleData;
		this.outputDirectory = outputDirectory;
	}

	public String getImdbShowIDString() {
		return imdbShowIDString;
	}

	public void setImdbShowIDString(String imdbShowIDString) {
		this.imdbShowIDString = imdbShowIDString;
	}

	public int getSeasonNumber() {
		return seasonNumber;
	}

	public void setSeasonNumber(int seasonNumber) {
		this.seasonNumber = seasonNumber;
	}

	public int getEpisodeNumber() {
		return episodeNumber;
	}

	public void setEpisodeNumber(int episodeNumber) {
		this.episodeNumber = episodeNumber;
	}

	public OpenSubtitles_Data getSubtitleData() {
		return subtitleData;
	}

	public void setSubtitleData(OpenSubtitles_Data subtitleData) {
		this.subtitleData = subtitleData;
	}

	public File getOutputDirectory() {
		return outputDirectory;
	}

	public void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
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
}
