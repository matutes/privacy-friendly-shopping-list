package privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.impl;

import android.content.Context;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.R;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.framework.comparators.PFAComparators;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.framework.persistence.DB;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.ProductService;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.domain.AutoCompleteLists;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.domain.ProductDto;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.domain.TotalDto;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.impl.comparators.ProductComparators;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.business.impl.converter.ProductConverterService;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.persistence.ProductItemDao;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.product.persistence.entity.ProductItemEntity;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.shoppingList.business.ShoppingListService;
import privacyfriendlyshoppinglist.secuso.org.privacyfriendlyshoppinglist.logic.shoppingList.persistence.entity.ShoppingListEntity;
import rx.Observable;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * Description:
 * Author: Grebiel Jose Ifill Brito
 * Created: 17.07.16 creation date
 */
public class ProductServiceImpl implements ProductService
{
    private static final String DETAIL_SEPARATOR = ": ";
    private static final String NEW_LINE = "\n";
    private static final String SPACE = " ";

    private ProductItemDao productItemDao;
    private ProductConverterService converterService;
    private ShoppingListService shoppingListService;
    private Context context;

    @Inject
    public ProductServiceImpl(
            ProductItemDao productItemDao,
            ProductConverterService converterService,
            ShoppingListService shoppingListService
    )
    {
        this.productItemDao = productItemDao;
        this.converterService = converterService;
        this.shoppingListService = shoppingListService;
    }

    @Override
    public void setContext(Context context, DB db)
    {
        this.context = context;
        productItemDao.setContext(context, db);
        shoppingListService.setContext(context, db);
        converterService.setContext(context, db);
    }

    @Override
    public void saveOrUpdate(ProductDto dto, String listId)
    {
        ProductItemEntity entity = new ProductItemEntity();
        converterService.convertDtoToEntity(dto, entity);

        ShoppingListEntity shoppingListEntity = shoppingListService.getEntityById(listId);
        entity.setShoppingList(shoppingListEntity);

        productItemDao.save(entity);
        dto.setId(entity.getId().toString());
    }

    @Override
    public ProductDto getById(String entityId)
    {
        ProductItemEntity productEntity = productItemDao.getById(Long.valueOf(entityId));
        if ( productEntity == null ) return null;

        ProductDto dto = new ProductDto();
        converterService.convertEntitiesToDto(productEntity, dto);
        return dto;
    }

    @Override
    public void deleteById(String id)
    {
        productItemDao.deleteById(Long.valueOf(id));
    }

    @Override
    public void deleteSelected(List<ProductDto> productDtos)
    {
        Observable.from(productDtos)
                .filter(dto -> dto.isSelectedForDeletion())
                .subscribe(dto -> deleteById(dto.getId()));
    }

    @Override
    public List<ProductDto> getAllProducts(String listId)
    {
        Observable<ProductDto> dtos = Observable
                .from(productItemDao.getAllEntities())
                .filter(entity -> entity.getShoppingList().getId() == Long.valueOf(listId))
                .map(this::getDto);

        return dtos.toList().toBlocking().single();
    }

    @Override
    public String getInfo(String listId, String currency)
    {
        List<ProductDto> allProducts = getAllProducts(listId);
        TotalDto totalDto = computeTotals(allProducts);

        String nrItemsLabel = context.getResources().getString(R.string.nr_items);
        String totalAmountLabel = context.getResources().getString(R.string.total_list_amount);

        StringBuilder sb = new StringBuilder();
        sb.append(nrItemsLabel);
        sb.append(DETAIL_SEPARATOR);
        sb.append(totalDto.getNrProducts());
        sb.append(NEW_LINE);
        sb.append(totalAmountLabel);
        sb.append(DETAIL_SEPARATOR);
        sb.append(totalDto.getTotalAmount());
        sb.append(SPACE);
        sb.append(currency);
        sb.append(NEW_LINE);
        sb.append(NEW_LINE);

        return sb.toString();
    }

    @Override
    public void deleteAllFromList(String listId)
    {
        List<ProductDto> productDtos = getAllProducts(listId);
        Observable.from(productDtos)
                .subscribe(dto -> deleteById(dto.getId()));
    }

    @Override
    public List<ProductDto> moveSelectedToEnd(List<ProductDto> productDtos)
    {
        List<ProductDto> nonSelectedDtos = Observable
                .from(productDtos)
                .filter(dto -> !dto.isChecked())
                .toList().toBlocking().single();

        List<ProductDto> selectedDtos = Observable
                .from(productDtos)
                .filter(dto -> dto.isChecked())
                .toList().toBlocking().single();
        nonSelectedDtos.addAll(selectedDtos);
        productDtos = nonSelectedDtos;
        return productDtos;
    }

    @Override
    public TotalDto computeTotals(List<ProductDto> productDtos)
    {
        double totalAmount = 0.0;
        double checkedAmount = 0.0;
        int nrProducts = 0;
        for ( ProductDto dto : productDtos )
        {
            nrProducts++;
            String price = dto.getProductPrice();
            if ( price != null )
            {
                Integer quantity = Integer.valueOf(dto.getQuantity());
                double priceAsDouble = converterService.getStringAsDouble(price) * quantity;
                totalAmount += priceAsDouble;
                if ( dto.isChecked() )
                {
                    checkedAmount += priceAsDouble;
                }
            }
        }

        TotalDto totalDto = new TotalDto();

        if ( totalAmount == 0.0 )
        {
            totalDto.setEqualsZero(true);
        }
        totalDto.setTotalAmount(converterService.getDoubleAsString(totalAmount));
        totalDto.setCheckedAmount(converterService.getDoubleAsString(checkedAmount));
        totalDto.setNrProducts(nrProducts);

        return totalDto;
    }

    @Override
    public Observable<AutoCompleteLists> getAutoCompleteListsObservable()
    {
        Observable<AutoCompleteLists> autoCompleteListsObservable = Observable
                .create(subscriber ->
                {
                    subscriber.onNext(getAutoCompleteLists());
                    subscriber.onCompleted();
                });

        return autoCompleteListsObservable;
    }

    private AutoCompleteLists getAutoCompleteLists()
    {
        AutoCompleteLists autoCompleteLists = new AutoCompleteLists();

        Observable
                .from(productItemDao.getAllEntities())
                .map(this::getDto)
                .subscribe(
                        dto -> autoCompleteLists.updateLists(dto)
                );
        return autoCompleteLists;
    }

    @Override
    public void sortProducts(List<ProductDto> products, String criteria, boolean ascending)
    {
        if ( PFAComparators.SORT_BY_NAME.equals(criteria) )
        {
            Collections.sort(products, ProductComparators.getNameComparator(ascending));
        }
        else if ( PFAComparators.SORT_BY_QUANTITY.equals(criteria) )
        {
            Collections.sort(products, ProductComparators.getQuantityCompartor(ascending));
        }
        else if ( PFAComparators.SORT_BY_STORE.equals(criteria) )
        {
            Collections.sort(products, ProductComparators.getStoreComparator(ascending));
        }
        else if ( PFAComparators.SORT_BY_CATEGORY.equals(criteria) )
        {
            Collections.sort(products, ProductComparators.getCategoryComparator(ascending));
        }
        else if ( PFAComparators.SORT_BY_PRICE.equals(criteria) )
        {
            Collections.sort(products, ProductComparators.getPriceComparator(ascending, context));
        }
    }

    private ProductDto getDto(ProductItemEntity entity)
    {
        ProductDto dto = new ProductDto();
        converterService.convertEntitiesToDto(entity, dto);
        return dto;
    }
}
