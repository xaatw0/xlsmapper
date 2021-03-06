package com.gh.mygreen.xlsmapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.gh.mygreen.xlsmapper.annotation.XlsSheetName;
import com.gh.mygreen.xlsmapper.annotation.XlsSheet;
import com.gh.mygreen.xlsmapper.fieldprocessor.FieldAdaptor;
import com.gh.mygreen.xlsmapper.xml.AnnotationReadException;
import com.gh.mygreen.xlsmapper.xml.AnnotationReader;

/**
 * 読み込み時/書き込み処理時のシートを取得するクラス。
 * <p>アノテーション{@link XlsSheet}の設定値に従いシートを取得する。
 * 
 * @since 1.1
 * @author T.TSUCHIE
 *
 */
public class SheetFinder {
    
    /**
     * 読み込み時のシートを取得する。
     * 
     * @param workbook Excelのワークブック。
     * @param sheetAnno JavaBeanのクラスに付与されているアノテーション{@link XlsSheet}。
     * @param annoReader
     * @param beanClass JavaBeanのクラス。
     * @return Excelのシート情報。複数ヒットする場合は、該当するものを全て返す。
     * @throws SheetNotFoundException 該当のシートが見つからない場合にスローする。
     * @throws AnnotationInvalidException アノテーションの使用方法が不正な場合
     * @throws AnnotationReadException アノテーションをXMLで指定する方法が不正な場合。
     */
    public Sheet[] findForLoading(final Workbook workbook, final XlsSheet sheetAnno,
            final AnnotationReader annoReader, final Class<?> beanClass)
                    throws SheetNotFoundException, AnnotationInvalidException, AnnotationReadException {
        
        if(sheetAnno.name().length() > 0) {
            // シート名から取得する。
            final Sheet xlsSheet = workbook.getSheet(sheetAnno.name());
            if(xlsSheet == null) {
                throw new SheetNotFoundException(sheetAnno.name());
            }
            return new Sheet[]{ xlsSheet };
            
        } else if(sheetAnno.number() >= 0) {
            // シート番号から取得する
            if(sheetAnno.number() >= workbook.getNumberOfSheets()) {
                throw new SheetNotFoundException(sheetAnno.number(), workbook.getNumberOfSheets());
            }
            
            return new Sheet[]{ workbook.getSheetAt(sheetAnno.number()) }; 
            
        } else if(sheetAnno.regex().length() > 0) {
            // シート名（正規表現）をもとにして、取得する。
            final Pattern pattern = Pattern.compile(sheetAnno.regex());
            final List<Sheet> matches = new ArrayList<>();
            for(int i=0; i < workbook.getNumberOfSheets(); i++) {
                final Sheet xlsSheet = workbook.getSheetAt(i);
                if(pattern.matcher(xlsSheet.getSheetName()).matches()) {
                    matches.add(xlsSheet);
                }
            }
            
            if(matches.isEmpty()) {
                throw new SheetNotFoundException(sheetAnno.regex());
            }
            
            return matches.toArray(new Sheet[matches.size()]);
        }
        
        throw new AnnotationInvalidException(String.format("With '%s', @XlsSheet requires name or number or regex parameter.",
                beanClass.getName()), sheetAnno);
    }
    
    /**
     * 書き込み時のシートを取得する。
     * @param workbook Excelのワークブック。
     * @param sheetAnno JavaBeanのクラスに付与されているアノテーション{@link XlsSheet}。
     * @param annoReader
     * @param beanObj JavaBeanのオブジェクト。
     * @return Excelのシート情報。複数ヒットする場合は、該当するものを全て返す。
     * @throws SheetNotFoundException 該当のシートが見つからない場合にスローする。
     * @throws AnnotationInvalidException アノテーションの使用方法が不正な場合
     * @throws AnnotationReadException アノテーションをXMLで指定する方法が不正な場合。
     */
    public Sheet[] findForSaving(final Workbook workbook, final XlsSheet sheetAnno,
            final AnnotationReader annoReader, final Object beanObj)
                    throws SheetNotFoundException, AnnotationInvalidException, AnnotationReadException {
        
        if(sheetAnno.name().length() > 0) {
            // シート名から取得する。
            final Sheet xlsSheet = workbook.getSheet(sheetAnno.name());
            if(xlsSheet == null) {
                throw new SheetNotFoundException(sheetAnno.name());
            }
            return new Sheet[]{ xlsSheet };
            
        } else if(sheetAnno.number() >= 0) {
            // シート番号から取得する
            if(sheetAnno.number() >= workbook.getNumberOfSheets()) {
                throw new SheetNotFoundException(sheetAnno.number(), workbook.getNumberOfSheets());
            }
            
            return new Sheet[]{ workbook.getSheetAt(sheetAnno.number()) };
            
        } else if(sheetAnno.regex().length() > 0) {
            // シート名（正規表現）をもとにして、取得する。
            String sheetNameValue = null;
            FieldAdaptor sheetNameField = getSheetNameField(beanObj, annoReader);
            if(sheetNameField != null && sheetNameField.getValue(beanObj) != null) {
                sheetNameValue = sheetNameField.getValue(beanObj).toString();
            }
            
            final Pattern pattern = Pattern.compile(sheetAnno.regex());
            final List<Sheet> matches = new ArrayList<>();
            for(int i=0; i < workbook.getNumberOfSheets(); i++) {
                final Sheet xlsSheet = workbook.getSheetAt(i);
                if(pattern.matcher(xlsSheet.getSheetName()).matches()) {
                    
                    // オブジェクト中の@XslSheetNameで値が設定されている場合、Excelファイル中の一致するシートを元にする比較する
                    if(Utils.isNotEmpty(sheetNameValue) && xlsSheet.getSheetName().equals(sheetNameValue)) {
                        return new Sheet[]{ xlsSheet };
                        
                    }
                    
                    matches.add(xlsSheet);
                }
            }
            
            if(sheetNameValue != null && !matches.isEmpty()) {
                // シート名が直接指定の場合
                throw new SheetNotFoundException(sheetNameValue);
                
            } else if(matches.isEmpty()) {
                throw new SheetNotFoundException(sheetAnno.regex());
                
            } else if(matches.size() == 1) {
                // １つのシートに絞り込めた場合
                return new Sheet[]{ matches.get(0) };
                
            } else {
                // 複数のシートがヒットした場合
                List<String> names = new ArrayList<>();
                for(Sheet sheet : matches) {
                    names.add(sheet.getSheetName());
                }
                throw new SheetNotFoundException(sheetAnno.regex(),
                        String.format("found multiple sheet : %s.", Utils.join(names, ",")));
            }
        }
        
        throw new AnnotationInvalidException(String.format("With '%s', @XlsSheet requires name or number or regex parameter.",
                beanObj.getClass().getName()), sheetAnno);
    }
    
    /**
     * アノテーション「@XlsSheetName」が付与されているフィールド／メソッドを取得する。
     * @param beanObj
     * @param config
     * @param annoReader
     * @return
     * @throws AnnotationReadException 
     */
    private FieldAdaptor getSheetNameField(final Object beanObj, final AnnotationReader annoReader) throws AnnotationReadException {
        
        Class<?> clazz = beanObj.getClass();
        for(Method method : clazz.getMethods()) {
            method.setAccessible(true);
            if(!Utils.isGetterMethod(method)) {
                continue;
            }
            
            XlsSheetName sheetNameAnno = annoReader.getAnnotation(clazz, method, XlsSheetName.class);
            if(sheetNameAnno == null) {
                continue;
            }
            
            return new FieldAdaptor(clazz, method, annoReader);
        }
        
        for(Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            
            XlsSheetName sheetNameAnno = annoReader.getAnnotation(clazz, field, XlsSheetName.class);
            if(sheetNameAnno == null) {
                continue;
            }
            
            return new FieldAdaptor(clazz, field, annoReader);
        }
        
        // not found
        return null;
    }
    
}
