package run_ffmpeg;

import java.util.Comparator;

public class TranscodeFileSortLargeToSmall implements Comparator< TranscodeFile >
{
	@Override
	public int compare( TranscodeFile lhs, TranscodeFile rhs )
	{
		if ( lhs.getMKVInputFile().length() > rhs.getMKVInputFile().length() )
		{
			return -1;
		}
		else if (lhs.getMKVInputFile().length() < rhs.getMKVInputFile().length() )
		{
			return 1;
		}
		else
		{
			return 0;
		}
	}
}
