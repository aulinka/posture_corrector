package sk.umb.bachelor.degree.posture_corrector;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {DayStatistic.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "posture-corrector-db")
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return instance;
    }


    public abstract DayStatisticDao dayStatisticDao();
}
