package run_ffmpeg;

/**
 * Encapsulate several data items to use when transcoding a file to prevent repeated database queries.
 */
public class TranscodeAndMoveFileInfo
{
	public TranscodeFile fileToTranscode = null ;
	public FFmpegProbeResult mkvProbeInfo = null ;
	public MovieAndShowInfo movieAndShowInfo = null ;

	public TranscodeAndMoveFileInfo()
	{}
	
	public TranscodeAndMoveFileInfo( TranscodeFile fileToTranscode,
			FFmpegProbeResult mkvProbeInfo,
			MovieAndShowInfo movieAndShowInfo )
	{
		this.fileToTranscode = fileToTranscode ;
		this.mkvProbeInfo = mkvProbeInfo ;
		this.movieAndShowInfo = movieAndShowInfo ;
	}
	
	@Override
	public String toString()
	{
		String retMe = "TranscodeAndMoveFileInfo: {"
				+ fileToTranscode.toString() + ","
				+ mkvProbeInfo.toString() + ","
				+ movieAndShowInfo.toString() + "}" ;
		return retMe ;
	}
}
