package com.ll.framework.ioc;
import com.ll.framework.ioc.annotations.Component;
import com.ll.standard.util.Ut;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationContext {
    String beanPackage;
    // 객체 저장 장소
    Map<String, Object> beans;
    public ApplicationContext(String basePackage) {
        this.beanPackage = basePackage;
    }

    public void init() {
        beans = new HashMap<>();
        addBeans(Component.class);
    }

    /**
     * 객체 생성하는 메소드
     * @param clazz
     */
    private void makeBean(Class<?> clazz){
        if (hasBean(clazz)) {
            return;
        }
        try{
            List<Object> values = new ArrayList<>();
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> constructor : constructors) {
                // Component를 어노테이션한 클래스 만으로 생성자를 생성 가능한지?
                boolean found = false;
                Parameter[] parameters = constructor.getParameters();
                for (Parameter parameter : parameters) {
                    Class<?> parameterType = parameter.getType();
                    if (findAnnotation(parameterType, Component.class)){
                        if (!hasBean(parameterType)) {
                            makeBean(parameterType);
                        }
                    }else{
                        found = true;
                    }
                }
                if (found) {
                    continue;
                }
                for (Parameter parameter : parameters) {
                    Class<?> type = parameter.getType();
                    values.add(getBean(type));
                }
                Object obj = constructor.newInstance(values.toArray(new Object[values.size()]));
                putBean(clazz, obj);
                break;
            }

        } catch (Exception e) {

        }

    }

    private void putBean(Class<?> clazz, Object bean) {
        beans.put(Ut.str.lcfirst(clazz.getSimpleName()), bean);
    }

    private Object getBean(Class<?> clazz) {
        return beans.get(Ut.str.lcfirst(clazz.getSimpleName()));
    }

    public <T> T genBean(String beanName) {
        // 아냐 걱정할 필요 없어 아마 맞을거야
        return (T) beans.get(beanName);
    }

    private boolean hasBean(Class<?> clazz) {
        return beans.containsKey(Ut.str.lcfirst(clazz.getSimpleName()));
    }

    /**
     * 어노테이션된 클래스 찾아서 객체로 만들어 저장
     * @param annotation
     */
    private void addBeans(Class<? extends Annotation> annotation) {
        try{
            List<Class<?>> classes = getAnnotatedClasses(beanPackage, annotation);
            for (Class<?> clazz : classes) {
                if (!hasBean(clazz)) {
                    makeBean(clazz);
                }
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    /**
     * 어노테이션을 따라가면서 어노테이션을 찾아냄
     * @param clazz
     * @param annotation
     * @return
     */
    private boolean findAnnotation(Class<?> clazz, Class<? extends Annotation> annotation) {
        if (clazz.isAnnotationPresent(annotation)) {
            return true;
        }
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation1 : annotations) {
            if (annotation1.annotationType().equals(clazz)) {
                return false;
            }
            // 어노테이션의 어노테이션 찾기
            boolean result = findAnnotation(annotation1.annotationType(), annotation);
            if (result) {
                return true;
            }
        }
        return false;
    }

    /**
     * 인터넷에 검색해서 찾아낸 결과가 이거인데 작성하고 나서 다른 분이 작성한 코드를 보니까 Reflections::getTypesAnnotatedWith 메소드를 사용하던데
     * 열심히 만든 노력이 아까워서 남겨둘 예정
     * @param packageName
     * @param annotation
     * @return
     */
    private List<Class<?>> getAnnotatedClasses(String packageName, Class<? extends Annotation> annotation) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        String packagePath = packageName.replace('.', '/');
        // 클래스 파일이 저장된 폴더
        URL resource = ClassLoader.getSystemClassLoader().getResource(packagePath);

        if (resource == null) {
            return classes;
        }
        File dir = new File(resource.getPath());
        if (!dir.exists() && !dir.isDirectory()) {
            return classes;
        }
        try{
            // class 파일 위치 다 뒤져서 찾아냄
            List<String> paths = Files
                    .walk(Paths.get(dir.getPath()))
                    .filter(
                            (path) ->
                                    path.getFileName().toString().endsWith(".class")
                    ).map((Path::toString))
                    .toList();
            for (String path : paths) {
                int index = path.indexOf(packageName.replace('.', '\\'));
                // 패키지 이전의 절대경로를 잘라냄
                String className = path.substring(index, path.length() - 6);
                try{
                    // 경로를 패키지 표현 방법에 맞게 수정
                    Class<?> clazz= Class.forName(className.replace('\\', '.'));
                    if (findAnnotation(clazz, annotation)) {
                        classes.add(clazz);
                    }

                }catch (ClassNotFoundException e) {

                }
            }

        }catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return classes;
    }
}
