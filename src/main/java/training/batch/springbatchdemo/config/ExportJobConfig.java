package training.batch.springbatchdemo.config;

import training.batch.springbatchdemo.dto.BookDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.*;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.file.transform.FieldExtractor;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableBatchProcessing
public class ExportJobConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportJobConfig.class);
    private static final String PROPERTY_CSV_EXPORT_FILE_HEADER = "id;title;author;isbn;publisher;publishedOn";
    //private static final String PROPERTY_CSV_EXPORT_FILE_PATH = "database.to.csv.job.export.file.path";
    private static final String PROPERTY_CSV_EXPORT_FILE_PATH = "c:/dojo/buk.csv";

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private MaClassMetier maClassMetier;

    @Bean(name = "exportJob")
    public Job exportBookJob(final JobCompletionNotificationListener listener, final Step databaseToCsvFileStep){
        return jobBuilderFactory.get("export-Job")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
               .flow(databaseToCsvFileStep)
//                .step(exportStep)
                .end()
                .build();
    }

    @Bean
    ItemReader<BookDto> databaseCsvItemReader(DataSource dataSource) {
        JdbcPagingItemReader<BookDto> databaseReader = new JdbcPagingItemReader<>();

        databaseReader.setDataSource(dataSource);
        databaseReader.setPageSize(1);
        databaseReader.setRowMapper(new BeanPropertyRowMapper<>(BookDto.class));

        PagingQueryProvider queryProvider = createQueryProvider();
        databaseReader.setQueryProvider(queryProvider);

        return databaseReader;
    }
    private PagingQueryProvider createQueryProvider() {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();

        queryProvider.setSelectClause("SELECT id,title,author,isbn,publisher,publishedOn ");
        queryProvider.setFromClause("FROM book");
        queryProvider.setSortKeys(sortByTitle());
        return queryProvider;
    }
    private Map<String, Order> sortByTitle() {
        Map<String, Order> sortConfiguration = new HashMap<>();
        sortConfiguration.put("title", Order.ASCENDING);
        return sortConfiguration;
    }
    @Bean
    ItemWriter<BookDto> databaseCsvItemWriter(Environment environment) {
        FlatFileItemWriter<BookDto> csvFileWriter = new FlatFileItemWriter<>();

        //String exportFileHeader = environment.getRequiredProperty(PROPERTY_CSV_EXPORT_FILE_HEADER);
        //StringHeaderWriter headerWriter = new StringHeaderWriter(exportFileHeader);
        StringHeaderWriter headerWriter = new StringHeaderWriter(PROPERTY_CSV_EXPORT_FILE_HEADER);
        csvFileWriter.setHeaderCallback(headerWriter);

        //String exportFilePath = environment.getRequiredProperty(PROPERTY_CSV_EXPORT_FILE_PATH);
        //csvFileWriter.setResource(new FileSystemResource(exportFilePath));
        //String exportFilePath = environment.getRequiredProperty(PROPERTY_CSV_EXPORT_FILE_PATH);
        csvFileWriter.setResource(new FileSystemResource(PROPERTY_CSV_EXPORT_FILE_PATH));

        LineAggregator<BookDto> lineAggregator = createLineAggregator();
        csvFileWriter.setLineAggregator(lineAggregator);

        return csvFileWriter;
    }
    private LineAggregator<BookDto> createLineAggregator() {
        DelimitedLineAggregator<BookDto> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter(";");

        FieldExtractor<BookDto> fieldExtractor = createFieldExtractor();
        lineAggregator.setFieldExtractor(fieldExtractor);

        return lineAggregator;
    }

    private FieldExtractor<BookDto> createFieldExtractor() {
        BeanWrapperFieldExtractor<BookDto> extractor = new BeanWrapperFieldExtractor<>();
        extractor.setNames(new String[] {"id","title","author","isbn","publisher","publishedOn"});
        return extractor;
    }

    /*
    @StepScope //Mnadatory for using jobParameters
    @Bean
    public FlatFileItemReader<BookDto> exportReader( @Value("#{jobParameters['input-file']}") final String inputFile){
        return new FlatFileItemReaderBuilder<BookDto>()
               .name("bookItemReader")
               .resource(new FileSystemResource(inputFile))
                .delimited()
                .delimiter(";")
                .names(new String[]{ "title","author","isbn","publisher","publishedOn"}).linesToSkip(1)
                .fieldSetMapper(new BeanWrapperFieldSetMapper<BookDto>() {
                    {
                    setTargetType(BookDto .class);
                    }
                }).build();
    }*/
    @Bean
    Step databaseToCsvFileStep(ItemReader<BookDto> databaseCsvItemReader,
                               ItemProcessor<BookDto, BookDto> databaseCsvItemProcessor,
                               ItemWriter<BookDto> databaseCsvItemWriter,
                               StepBuilderFactory stepBuilderFactory) {
        return stepBuilderFactory.get("databaseToCsvFileStep")
                .<BookDto, BookDto>chunk(1)
                .reader(databaseCsvItemReader)
                .processor(databaseCsvItemProcessor)
                .writer(databaseCsvItemWriter)
                .build();
    }


    @Bean
    ItemProcessor<BookDto,BookDto> databaseCsvItemProcessor(){
        return new ItemProcessor<BookDto, BookDto>() {
            @Override
            public BookDto process(final BookDto book) throws Exception {
                LOGGER.info("BookDto {}",book );
                return maClassMetier.maMethodMetier(book);
                //return item;
            }
        };
    }
 /*
    @Bean
    public JdbcBatchItemWriter<BookDto> exportwriter(final DataSource dataSource){
        return new JdbcBatchItemWriterBuilder<BookDto>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("insert into book(title,author,isbn,publisher,publishedOn) "+
                        "values (:title,:author,:isbn,:publisher,:publishedOn)")
                        .dataSource(dataSource)
                .build();

    }*/

}
