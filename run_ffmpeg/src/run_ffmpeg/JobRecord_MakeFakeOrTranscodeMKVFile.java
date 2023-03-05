package run_ffmpeg;

public class JobRecord_MakeFakeOrTranscodeMKVFile
{
	public String mkvLongPath = null ;
	public String mp4LongPath = null ;
	public String movieOrShowName_id = null ;
	public String movieOrShowName = null ;
	public String mkvFileName = null ;
	public String fileName = null ;
	public String missingMKVFile = null ;
	public String missingMP4File = null ;	
	
	public String toString()
	{
		String retMe = "{"
				+ mkvLongPath +","
				+ mp4LongPath + ","
				+ movieOrShowName_id + ","
				+ movieOrShowName + ","
				+ mkvFileName + ","
				+ fileName + ","
				+ missingMKVFile + ","
				+ missingMP4File
				+ "}" ;
		return retMe ;
	}
}
